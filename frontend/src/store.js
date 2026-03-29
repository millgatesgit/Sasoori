/**
 * SASOORI — Global State Store
 * Simple module-pattern store. No framework.
 * Usage:
 *   import store from './store.js';
 *   store.setState({ cartCount: 3 });
 *   store.subscribe('cartCount', (val) => console.log(val));
 */

const _state = {
  user:       null,       // { id, name, email, picture, role }
  accessToken: null,      // JWT access token (in-memory only, never localStorage)
  cart:       [],         // [{ productId, name, weight, price, qty, image }]
  cartCount:  0,
  toast:      null,       // { type, title, message }
};

const _listeners = {};   // { key: [fn, fn, ...] }

const store = {
  /**
   * Read a state value
   * @param {string} key
   * @returns {*}
   */
  get(key) {
    return _state[key];
  },

  /**
   * Read the entire state snapshot (readonly copy)
   * @returns {object}
   */
  getState() {
    return { ..._state };
  },

  /**
   * Update one or more state keys and notify subscribers
   * @param {object} patch
   */
  setState(patch) {
    const changed = [];
    for (const [key, val] of Object.entries(patch)) {
      if (_state[key] !== val) {
        _state[key] = val;
        changed.push(key);
      }
    }
    changed.forEach(key => _notify(key, _state[key]));
  },

  /**
   * Subscribe to a specific state key
   * @param {string} key
   * @param {function} fn  Called with (newValue, key)
   * @returns {function} unsubscribe
   */
  subscribe(key, fn) {
    if (!_listeners[key]) _listeners[key] = [];
    _listeners[key].push(fn);
    return () => {
      _listeners[key] = _listeners[key].filter(f => f !== fn);
    };
  },

  // ── Auth helpers ───────────────────────────────────────
  setUser(user, accessToken) {
    this.setState({ user, accessToken });
  },

  clearUser() {
    this.setState({ user: null, accessToken: null });
  },

  isLoggedIn() {
    return !!_state.accessToken;
  },

  isAdmin() {
    return _state.user?.role === 'ADMIN';
  },

  // ── Cart helpers ───────────────────────────────────────
  setCart(items) {
    const count = items.reduce((sum, i) => sum + i.quantity, 0);
    this.setState({ cart: items, cartCount: count });
  },

  getCartCount() {
    return _state.cartCount;
  },
};

function _notify(key, val) {
  (_listeners[key] || []).forEach(fn => {
    try { fn(val, key); } catch (e) { console.error('Store subscriber error:', e); }
  });
}

export default store;
