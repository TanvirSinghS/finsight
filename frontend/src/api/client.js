const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const TOKEN_KEY = 'finsight.token';
const DASHBOARD_KEY = 'finsight.dashboard';
const DASHBOARD_CACHE_MS = 10 * 60 * 1000;

let warmStarted = false;
let dashboardRequest = null;

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

/**
 * Fire-and-forget ping so a sleeping free-tier backend — and its database —
 * starts waking while the user is still on the login screen. Runs once per page
 * load. The actuator health check also touches the database.
 */
export function warmUp() {
  if (warmStarted) return;
  warmStarted = true;
  // index.html fires the same ping in an inline script before this bundle loads.
  if (window.__finsightWarm) return;
  fetch(`${BASE_URL}/actuator/health`, { headers: { Accept: 'application/json' }, cache: 'no-store' }).catch(() => {});
}

async function request(path, { method = 'GET', body, headers = {}, raw = false, signal } = {}) {
  const token = getToken();
  const isForm = body instanceof FormData;
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    signal,
    headers: {
      Accept: 'application/json',
      ...(body && !isForm ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: isForm ? body : body ? JSON.stringify(body) : undefined,
  });

  if (response.status === 401) {
    setToken(null);
    window.dispatchEvent(new CustomEvent('finsight:unauthorized'));
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      message = data.message || message;
      if (data.fieldErrors) {
        const details = Object.values(data.fieldErrors).join(' ');
        if (details) message = `${message}: ${details}`;
      }
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  if (raw) return response;
  if (response.status === 204) return null;
  const type = response.headers.get('content-type') || '';
  return type.includes('application/json') ? response.json() : response.text();
}

function cacheDashboard(data) {
  try {
    localStorage.setItem(DASHBOARD_KEY, JSON.stringify({ at: Date.now(), data }));
  } catch {
    /* storage full or unavailable — the cache is an optimisation only */
  }
}

/**
 * Last dashboard payload, so a returning user sees their figures immediately
 * instead of waiting on a cold backend. Stale data is refreshed in the
 * background once the request completes.
 */
export function readDashboardCache() {
  try {
    const parsed = JSON.parse(localStorage.getItem(DASHBOARD_KEY) || 'null');
    if (!parsed?.data || Date.now() - parsed.at > DASHBOARD_CACHE_MS) return null;
    return parsed.data;
  } catch {
    return null;
  }
}

/**
 * Fetch the dashboard once per navigation burst: the login flow starts this
 * request while the route is still changing, and the dashboard page joins the
 * same promise instead of firing a duplicate request.
 */
export function fetchDashboard() {
  if (!dashboardRequest) {
    dashboardRequest = request('/api/dashboard')
      .then((data) => {
        cacheDashboard(data);
        return data;
      })
      .finally(() => {
        dashboardRequest = null;
      });
  }
  return dashboardRequest;
}

export function clearDashboardCache() {
  try {
    localStorage.removeItem(DASHBOARD_KEY);
  } catch {
    /* ignore */
  }
}

export const api = {
  get: (path, options) => request(path, options),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
  download: (path) => request(path, { raw: true, headers: { Accept: '*/*' } }),
};

export function queryString(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}
