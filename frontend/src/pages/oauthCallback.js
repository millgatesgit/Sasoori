/**
 * SASOORI — OAuth Callback Page
 * Route: #/oauth/callback
 * Handles Google OAuth2 PKCE code exchange.
 */

import store from '../store.js';
import { showToast } from '../components/toast.js';
import api from '../api/apiClient.js';

const page = {
  meta: () => ({ title: 'Signing In…', description: '' }),

  async render(container) {
    container.innerHTML = `
      <div style="min-height:50vh;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:var(--sp-4)">
        <div class="skeleton" style="width:48px;height:48px;border-radius:50%"></div>
        <p style="color:var(--clr-text-m)">Signing you in…</p>
      </div>`;
  },

  async onMount() {
    const hash    = window.location.hash || '';
    const qStr    = hash.includes('?') ? hash.slice(hash.indexOf('?') + 1) : '';
    const params  = new URLSearchParams(qStr);
    const code    = params.get('code');
    const state   = params.get('state');
    const error   = params.get('error');

    if (error || !code) {
      showToast('error', 'Login failed', error || 'No authorisation code received.');
      window.location.hash = '/login';
      return;
    }

    const savedState    = sessionStorage.getItem('oauth_state');
    const codeVerifier  = sessionStorage.getItem('oauth_verifier');

    if (state !== savedState) {
      showToast('error', 'Security error', 'State mismatch. Please try again.');
      window.location.hash = '/login';
      return;
    }

    sessionStorage.removeItem('oauth_state');
    sessionStorage.removeItem('oauth_verifier');

    try {
      const data = await api.post('/auth/google', { code, codeVerifier, state });
      const { accessToken, user } = data || {};
      if (accessToken && user) {
        store.setUser(user, accessToken);
        showToast('success', `Welcome, ${user.name?.split(' ')[0] || 'back'}!`, 'You are now signed in.');
      }
      window.location.hash = '/';
    } catch (err) {
      showToast('error', 'Login failed', err.message || 'Could not sign in. Please try again.');
      window.location.hash = '/login';
    }
  },

  onDestroy() {},
};

export default page;
