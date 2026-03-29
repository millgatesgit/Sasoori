/**
 * SASOORI — App Entry Point
 * Bootstraps the application: auth restore, router init, UI bindings.
 */

import { initRouter } from './router.js';
import store from './store.js';
import { showToast } from './components/toast.js';
import api from './api/apiClient.js';
import { getCart } from './api/cartApi.js';

async function bootstrap() {
  // 0. Apply saved theme (anti-FOWT already ran inline in <head>, this wires the toggle button)
  initTheme();

  // 1. Subscribe to store changes BEFORE restoreSession so notifications aren't missed
  store.subscribe('user',      updateHeaderUser);
  store.subscribe('cartCount', updateCartBadge);

  // 2. Try to restore session from refresh token cookie
  await restoreSession();

  // 3. Init router (resolves current URL)
  initRouter();

  // 4. Wire up global UI
  bindHeaderUI();
  bindSearchUI();

  // 5. Show app (removes FOUC prevention opacity)
  document.body.classList.add('ready');
}

/** Wire the theme toggle button and sync icon state */
function initTheme() {
  const btn = document.getElementById('themeToggleBtn');
  if (!btn) return;
  btn.addEventListener('click', toggleTheme);
}

/** Toggle between dark and light, persist to localStorage */
function toggleTheme() {
  const root    = document.documentElement;
  const current = root.getAttribute('data-theme');
  // If currently dark (explicit or OS), switch to light; otherwise switch to dark
  const isDark  = current === 'dark' ||
    (!current && window.matchMedia('(prefers-color-scheme: dark)').matches);
  const next    = isDark ? 'light' : 'dark';
  root.setAttribute('data-theme', next);
  localStorage.setItem('sasoori-theme', next);
  document.getElementById('themeToggleBtn')
    ?.setAttribute('aria-label', next === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
}

/** Attempt to restore session silently via refresh token cookie */
async function restoreSession() {
  try {
    const data = await api.post('/auth/refresh');
    const accessToken = data?.accessToken;
    const user = data?.user;

    if (accessToken && user) {
      store.setUser(user, accessToken);
      // Reload cart count
      try {
        const cart = await getCart();
        if (cart?.items) store.setCart(cart.items);
      } catch { /* cart load failure is non-critical */ }
    }
  } catch { /* no session — anonymous user, continue */ }
}

/** Update header to show user avatar or login button */
function updateHeaderUser(user) {
  const loginBtn   = document.getElementById('loginBtn');
  const userAvatar = document.getElementById('userAvatar');
  const avatarImg  = document.getElementById('userAvatarImg');

  if (user) {
    loginBtn?.classList.add('hidden');
    userAvatar?.classList.remove('hidden');
    if (avatarImg && user.picture) {
      avatarImg.src = user.picture;
      avatarImg.alt = user.name || 'Profile';
    }
    // User avatar click → go to orders
    userAvatar?.addEventListener('click', () => {
      window.location.hash = '/orders';
    }, { once: false });
  } else {
    loginBtn?.classList.remove('hidden');
    userAvatar?.classList.add('hidden');
  }
}

/** Keep cart badge in sync */
function updateCartBadge(count) {
  const badge = document.getElementById('cartBadge');
  if (!badge) return;
  badge.textContent  = count || 0;
  badge.dataset.count = count || 0;
  badge.setAttribute('aria-label', `${count || 0} items in cart`);
}

/** Header burger menu toggle */
function bindHeaderUI() {
  const menuBtn    = document.getElementById('menuBtn');
  const mobileMenu = document.getElementById('mobileMenu');

  menuBtn?.addEventListener('click', () => {
    const isOpen = mobileMenu.classList.toggle('open');
    menuBtn.classList.toggle('open', isOpen);
    menuBtn.setAttribute('aria-expanded', isOpen);
  });

  // Close menu on nav link click
  mobileMenu?.querySelectorAll('.mobile-nav-link').forEach(link => {
    link.addEventListener('click', () => {
      mobileMenu.classList.remove('open');
      menuBtn?.classList.remove('open');
      menuBtn?.setAttribute('aria-expanded', 'false');
    });
  });

  // Close menu on outside click
  document.addEventListener('click', (e) => {
    if (!menuBtn?.contains(e.target) && !mobileMenu?.contains(e.target)) {
      mobileMenu?.classList.remove('open');
      menuBtn?.classList.remove('open');
    }
  });
}

/** Search overlay */
function bindSearchUI() {
  const searchBtn     = document.getElementById('searchBtn');
  const bottomSearch  = document.getElementById('bottomSearch');
  const searchOverlay = document.getElementById('searchOverlay');
  const searchClose   = document.getElementById('searchClose');
  const searchInput   = document.getElementById('searchInput');

  const openSearch = () => {
    searchOverlay?.classList.add('open');
    setTimeout(() => searchInput?.focus(), 100);
  };

  const closeSearch = () => {
    searchOverlay?.classList.remove('open');
    if (searchInput) searchInput.value = '';
    const results = document.getElementById('searchResults');
    if (results) results.innerHTML = '';
  };

  searchBtn?.addEventListener('click', openSearch);
  bottomSearch?.addEventListener('click', openSearch);
  searchClose?.addEventListener('click', closeSearch);

  // Close on backdrop click
  searchOverlay?.addEventListener('click', (e) => {
    if (e.target === searchOverlay) closeSearch();
  });

  // Close on Escape
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && searchOverlay?.classList.contains('open')) closeSearch();
  });

  // Debounced search
  let debounceTimer;
  searchInput?.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    const q = searchInput.value.trim();
    if (!q) {
      document.getElementById('searchResults').innerHTML = '';
      return;
    }
    debounceTimer = setTimeout(() => doSearch(q), 300);
  });

  // Submit search → go to products page
  searchInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const q = searchInput.value.trim();
      if (q) {
        closeSearch();
        window.location.hash = `/products?search=${encodeURIComponent(q)}`;
      }
    }
  });
}

async function doSearch(query) {
  const resultsEl = document.getElementById('searchResults');
  if (!resultsEl) return;

  resultsEl.innerHTML = `<p style="color:var(--clr-text-m);font-size:var(--ts-sm);padding:var(--sp-2)">Searching…</p>`;

  try {
    const data = await api.get(`/products?search=${encodeURIComponent(query)}&size=5`);
    const products = data?.products || [];

    if (!products.length) {
      resultsEl.innerHTML = `<p style="color:var(--clr-text-m);font-size:var(--ts-sm);padding:var(--sp-2)">No results for "${query}"</p>`;
      return;
    }

    resultsEl.innerHTML = `
      <ul style="list-style:none">
        ${products.map(p => `
          <li>
            <a href="#/product/${p.slug}" style="
              display:flex;align-items:center;gap:var(--sp-3);
              padding:var(--sp-3);border-radius:var(--r-md);
              transition:background var(--t-fast);
            " onmouseover="this.style.background='var(--clr-bg)'"
               onmouseout="this.style.background=''">
              <div style="width:48px;height:48px;border-radius:var(--r-sm);background:var(--clr-divider);overflow:hidden;flex-shrink:0">
                ${p.images?.[0]
                  ? `<img src="${p.images[0]}" style="width:100%;height:100%;object-fit:cover" loading="lazy" alt="${p.name}" />`
                  : `<div style="width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-size:1.5rem">🫙</div>`}
              </div>
              <div>
                <div style="font-size:var(--ts-sm);font-weight:500;color:var(--clr-text-h)">${p.name}</div>
                <div style="font-size:var(--ts-xs);color:var(--clr-text-m)">${(p.weightGrams ?? p.weight_grams) ? (p.weightGrams ?? p.weight_grams) + 'g' : ''}</div>
              </div>
              <div style="margin-left:auto;font-size:var(--ts-sm);font-weight:600;color:var(--clr-primary)">
                ₹${Math.round((p.pricePaise ?? p.price_paise) / 100)}
              </div>
            </a>
          </li>
        `).join('')}
        <li style="border-top:1px solid var(--clr-divider);margin-top:var(--sp-2);padding-top:var(--sp-2)">
          <a href="#/products?search=${encodeURIComponent(query)}"
             style="display:block;padding:var(--sp-3);font-size:var(--ts-sm);color:var(--clr-primary);font-weight:500">
            View all results for "${query}" →
          </a>
        </li>
      </ul>`;

    // Close overlay on result click
    resultsEl.querySelectorAll('a').forEach(a => {
      a.addEventListener('click', () => {
        document.getElementById('searchOverlay')?.classList.remove('open');
      });
    });
  } catch {
    resultsEl.innerHTML = `<p style="color:var(--clr-error);font-size:var(--ts-sm);padding:var(--sp-2)">Search failed. Please try again.</p>`;
  }
}

// ── Start ────────────────────────────────────────────────────
bootstrap().catch(err => {
  console.error('Bootstrap failed:', err);
  document.body.classList.add('ready');
});
