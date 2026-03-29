/**
 * SASOORI — API Client
 * Central fetch wrapper with:
 *  - Auto Bearer token injection
 *  - 401 → auto refresh token → retry once
 *  - Queuing concurrent requests during token refresh
 *  - Standardised error throwing
 */

import store from '../store.js';

const _isDev = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
const BASE_URL = _isDev ? 'http://localhost:9090/api/v1' : '/api/v1';

let _isRefreshing = false;
let _refreshQueue = [];   // [{resolve, reject}]

/**
 * Core request function
 * @param {string} method
 * @param {string} path   e.g. '/products?page=1'
 * @param {object} [body]
 * @param {object} [opts] extra fetch options
 * @returns {Promise<any>} parsed response data
 */
export async function request(method, path, body = null, opts = {}) {
  const url = BASE_URL + path;
  const headers = {
    'Content-Type': 'application/json',
    ...opts.headers,
  };

  const token = store.get('accessToken');
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const fetchOpts = {
    method,
    headers,
    credentials: 'include',   // sends HttpOnly refresh token cookie
    ...opts,
  };

  if (body && method !== 'GET') {
    fetchOpts.body = JSON.stringify(body);
  }

  let response = await fetch(url, fetchOpts);

  // Token expired — refresh and retry once
  if (response.status === 401 && !opts._retry) {
    const newToken = await _refreshAccessToken();
    if (newToken) {
      return request(method, path, body, { ...opts, _retry: true });
    } else {
      // Refresh failed — force logout
      store.clearUser();
      window.location.hash = '/login';
      throw new ApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
    }
  }

  // Parse response
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : {}; }
  catch { data = { error: { code: 'PARSE_ERROR', message: text } }; }

  if (!response.ok) {
    const err = data?.error || {};
    throw new ApiError(response.status, err.code || 'API_ERROR', err.message || `Request failed (${response.status})`);
  }

  return data?.data ?? data;
}

/** Refresh the access token using the HttpOnly refresh cookie */
async function _refreshAccessToken() {
  if (_isRefreshing) {
    // Queue this request until the in-flight refresh completes
    return new Promise((resolve, reject) => {
      _refreshQueue.push({ resolve, reject });
    });
  }

  _isRefreshing = true;
  try {
    const res = await fetch(BASE_URL + '/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    });

    if (!res.ok) {
      _drainQueue(null);
      return null;
    }

    const json = await res.json();
    const newToken = json?.data?.accessToken;
    store.setState({ accessToken: newToken });
    _drainQueue(newToken);
    return newToken;
  } catch {
    _drainQueue(null);
    return null;
  } finally {
    _isRefreshing = false;
  }
}

function _drainQueue(token) {
  _refreshQueue.forEach(({ resolve, reject }) => token ? resolve(token) : reject());
  _refreshQueue = [];
}

// ── Convenience methods ─────────────────────────────────────
export const api = {
  get:    (path, opts)       => request('GET',    path, null, opts),
  post:   (path, body, opts) => request('POST',   path, body, opts),
  put:    (path, body, opts) => request('PUT',    path, body, opts),
  patch:  (path, body, opts) => request('PATCH',  path, body, opts),
  delete: (path, opts)       => request('DELETE', path, null, opts),
};

// ── Error class ─────────────────────────────────────────────
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name    = 'ApiError';
    this.status  = status;
    this.code    = code;
    this.message = message;
  }
}

export default api;
