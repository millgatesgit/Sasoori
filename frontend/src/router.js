/**
 * SASOORI — Hash Router
 * Handles #/path and #/path/:param routing with lifecycle hooks.
 *
 * Route definition: { path, page, auth, admin }
 *   path:  '/product/:slug'  (colon prefix = param)
 *   page:  async () => import('./pages/home.js')
 *   auth:  true  → require login
 *   admin: true  → require ADMIN role
 */

import store from './store.js';
import { showToast } from './components/toast.js';

const routes = [
  { path: '/',               page: () => import('./pages/home.js') },
  { path: '/products',       page: () => import('./pages/products.js') },
  { path: '/product/:slug',  page: () => import('./pages/productDetail.js') },
  { path: '/cart',           page: () => import('./pages/cart.js') },
  { path: '/checkout',       page: () => import('./pages/checkout.js'),  auth: true },
  { path: '/orders',         page: () => import('./pages/orders.js'),    auth: true },
  { path: '/orders/:id',     page: () => import('./pages/orderDetail.js'), auth: true },
  { path: '/profile',        page: () => import('./pages/profile.js'),  auth: true },
  { path: '/login',          page: () => import('./pages/login.js') },
  { path: '/oauth/callback', page: () => import('./pages/oauthCallback.js') },
  { path: '/admin',          page: () => import('./pages/admin/dashboard.js'), admin: true },
  { path: '/admin/products', page: () => import('./pages/admin/products.js'),  admin: true },
  { path: '/admin/orders',   page: () => import('./pages/admin/orders.js'),    admin: true },
  { path: '/admin/users',    page: () => import('./pages/admin/users.js'),     admin: true },
  { path: '/admin/shipments', page: () => import('./pages/admin/shipments.js'), admin: true },
  { path: '/admin/invoices',  page: () => import('./pages/admin/invoices.js'),  admin: true },
];

let _currentPage = null;   // current page instance (for onDestroy)
const main = document.getElementById('main');
const footer = document.getElementById('siteFooter');

/**
 * Parse the current hash into { path, params, query }
 */
function parseHash() {
  const raw = window.location.hash.slice(1) || '/';
  const [pathWithQuery] = raw.split('?');
  const queryString = raw.includes('?') ? raw.slice(raw.indexOf('?') + 1) : '';

  const path = pathWithQuery || '/';
  const query = Object.fromEntries(new URLSearchParams(queryString));
  return { path, query };
}

/**
 * Match a path against a route pattern, extract params
 */
function matchRoute(routePath, actualPath) {
  const routeParts = routePath.split('/').filter(Boolean);
  const actualParts = actualPath.split('/').filter(Boolean);

  if (routeParts.length !== actualParts.length) return null;

  const params = {};
  for (let i = 0; i < routeParts.length; i++) {
    if (routeParts[i].startsWith(':')) {
      params[routeParts[i].slice(1)] = decodeURIComponent(actualParts[i]);
    } else if (routeParts[i] !== actualParts[i]) {
      return null;
    }
  }
  return params;
}

/**
 * Main resolve function — called on hashchange and init
 */
async function resolve() {
  const { path, query } = parseHash();

  // Find matching route
  let matched = null;
  let params = {};

  for (const route of routes) {
    const p = matchRoute(route.path, path);
    if (p !== null) {
      matched = route;
      params = p;
      break;
    }
  }

  if (!matched) {
    render404();
    return;
  }

  // Auth guard
  if (matched.auth && !store.isLoggedIn()) {
    store.setState({ redirectAfterLogin: path + (Object.keys(query).length ? '?' + new URLSearchParams(query) : '') });
    window.location.hash = '/login';
    return;
  }

  // Admin guard
  if (matched.admin && !store.isAdmin()) {
    showToast('error', 'Access denied', 'Admin access required.');
    window.location.hash = '/';
    return;
  }

  // Destroy current page
  if (_currentPage?.onDestroy) {
    try { _currentPage.onDestroy(); } catch (e) { console.warn('onDestroy error:', e); }
  }

  // Show skeleton while loading
  showSkeleton();
  updateNavActive(path);

  try {
    const mod = await matched.page();
    const page = mod.default;

    // Render page
    main.innerHTML = '';
    await page.render(main, { params, query });
    _currentPage = page;

    // Mount lifecycle hook
    if (page.onMount) {
      try { await page.onMount({ params, query }); } catch (e) { console.error('onMount error:', e); }
    }

    // Hide storefront chrome on admin pages
    const isAdmin = path.startsWith('/admin');
    footer.style.display = isAdmin ? 'none' : '';
    document.body.classList.toggle('adm-mode', isAdmin);

    // Scroll to top
    window.scrollTo({ top: 0, behavior: 'instant' });

    // Update <title> and meta
    if (page.meta) updateMeta(page.meta({ params, query }));

  } catch (err) {
    console.error('Router: page load failed', err);
    renderError(err);
  }
}

function showSkeleton() {
  main.innerHTML = `
    <div class="container" style="padding-top:var(--sp-8)">
      <div class="product-grid">
        ${Array(8).fill(0).map(() => `
          <div class="skeleton-card">
            <div class="skeleton skeleton-img"></div>
            <div class="skeleton skeleton-text" style="width:80%"></div>
            <div class="skeleton skeleton-text skeleton-text-sm"></div>
            <div class="skeleton skeleton-text" style="width:40%"></div>
            <div class="skeleton skeleton-btn"></div>
          </div>`).join('')}
      </div>
    </div>`;
}

function render404() {
  main.innerHTML = `
    <div class="empty-state">
      <div class="empty-state-icon">🔍</div>
      <h2 class="empty-state-title">Page Not Found</h2>
      <p class="empty-state-desc">The page you're looking for doesn't exist.</p>
      <a href="#/" class="btn btn-primary" style="margin-top:var(--sp-4)">Go Home</a>
    </div>`;
  updateMeta({ title: '404 — Page Not Found' });
}

function renderError(err) {
  main.innerHTML = `
    <div class="empty-state">
      <div class="empty-state-icon">⚠️</div>
      <h2 class="empty-state-title">Something went wrong</h2>
      <p class="empty-state-desc">${err.message || 'An unexpected error occurred.'}</p>
      <a href="#/" class="btn btn-primary" style="margin-top:var(--sp-4)">Go Home</a>
    </div>`;
}

function updateNavActive(path) {
  // Desktop nav
  document.querySelectorAll('.nav-link').forEach(link => {
    const href = link.getAttribute('href')?.slice(1) || '/';
    link.classList.toggle('active', href === path || path.startsWith(href + '/'));
  });

  // Bottom nav
  document.querySelectorAll('.bottom-nav-item[data-route]').forEach(item => {
    const route = item.getAttribute('data-route');
    item.classList.toggle('active', route === path);
  });
}

function updateMeta({ title, description, image } = {}) {
  if (title) document.title = title + ' — Sasoori';
  if (description) {
    let el = document.querySelector('meta[name="description"]');
    if (el) el.setAttribute('content', description);
  }
  if (image) {
    let el = document.querySelector('meta[property="og:image"]');
    if (el) el.setAttribute('content', image);
  }
}

/**
 * Navigate programmatically
 * @param {string} path  e.g. '/product/groundnut-oil'
 */
export function navigate(path) {
  window.location.hash = path;
}

/**
 * Initialize the router
 */
export function initRouter() {
  window.addEventListener('hashchange', resolve);
  resolve(); // resolve current URL on load
}

export default { initRouter, navigate };
