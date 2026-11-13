const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const TOKEN_KEY = 'finsight.token';

let inflight = 0;
let slowTimer = null;

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

function startRequest() {
  inflight += 1;
  if (inflight === 1 && !slowTimer) {
    slowTimer = setTimeout(() => {
      window.dispatchEvent(new CustomEvent('finsight:slow', { detail: true }));
    }, 3500);
  }
}

function endRequest() {
  inflight = Math.max(0, inflight - 1);
  if (inflight === 0) {
    if (slowTimer) clearTimeout(slowTimer);
    slowTimer = null;
    window.dispatchEvent(new CustomEvent('finsight:slow', { detail: false }));
  }
}

async function request(path, { method = 'GET', body, headers = {}, raw = false, signal } = {}) {
  const token = getToken();
  const isForm = body instanceof FormData;
  startRequest();
  try {
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
  } finally {
    endRequest();
  }
}

export const api = {
  get: (path, options) => request(path, options),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
  download: (path) => request(path, { raw: true }),
};

export function queryString(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}
