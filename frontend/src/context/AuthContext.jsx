import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, clearDashboardCache, fetchDashboard, getToken, setToken, warmUp } from '../api/client.js';

const AuthContext = createContext(null);
const USER_KEY = 'finsight.user';

function readCachedUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function cacheUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  else localStorage.removeItem(USER_KEY);
}

export function AuthProvider({ children }) {
  // Render the saved profile immediately, then refresh it from the API.
  const [user, setUser] = useState(() => (getToken() ? readCachedUser() : null));
  const [loading, setLoading] = useState(() => Boolean(getToken()) && !readCachedUser());

  const logout = useCallback(() => {
    setToken(null);
    cacheUser(null);
    clearDashboardCache();
    setUser(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    warmUp();
    if (!getToken()) {
      cacheUser(null);
      setLoading(false);
      return undefined;
    }
    api
      .get('/api/auth/me')
      .then((profile) => {
        if (cancelled) return;
        setUser(profile);
        cacheUser(profile);
      })
      .catch((err) => {
        // A sleeping backend or flaky network is not a dead session: keep the
        // cached profile and let the next request decide.
        if (!cancelled && err.status === 401) logout();
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [logout]);

  useEffect(() => {
    const handler = () => {
      cacheUser(null);
      clearDashboardCache();
      setUser(null);
    };
    window.addEventListener('finsight:unauthorized', handler);
    return () => window.removeEventListener('finsight:unauthorized', handler);
  }, []);

  // Load the dashboard in parallel with the route change so it is ready the
  // moment the page mounts.
  const preloadDashboard = useCallback(() => {
    fetchDashboard().catch(() => {});
  }, []);

  const login = useCallback(async (email, password) => {
    const data = await api.post('/api/auth/login', { email, password });
    setToken(data.token);
    cacheUser(data.user);
    clearDashboardCache();
    preloadDashboard();
    setUser(data.user);
    return data.user;
  }, [preloadDashboard]);

  const register = useCallback(async (payload) => {
    const data = await api.post('/api/auth/register', payload);
    setToken(data.token);
    cacheUser(data.user);
    clearDashboardCache();
    setUser(data.user);
    return data.user;
  }, []);

  const value = useMemo(() => ({ user, loading, login, register, logout }), [user, loading, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
