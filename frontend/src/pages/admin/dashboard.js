/**
 * SASOORI — Admin Dashboard
 * Route: #/admin  (requires ADMIN role)
 */

import { getDashboard } from '../../api/adminApi.js';
import { formatPrice, formatDate } from '../../utils/formatters.js';

const page = {
  meta: () => ({ title: 'Admin Dashboard', description: '' }),

  render(container) {
    container.innerHTML = _shell(`
      <h1 class="adm-page-title">Dashboard</h1>
      <div id="admDashContent"><div class="adm-loading">Loading…</div></div>
    `);
    document.querySelectorAll('.adm-nav-link').forEach(l => {
      l.classList.toggle('active', l.dataset.route === '/admin');
    });
  },

  async onMount() {
    try {
      const data = await getDashboard();
      const d = data.data || data;
      _renderDashboard(d);
    } catch (err) {
      document.getElementById('admDashContent').innerHTML =
        `<div class="co-error">${err.message || 'Failed to load dashboard.'}</div>`;
    }
  },

  onDestroy() {},
};

export default page;

function _renderDashboard(d) {
  const el = document.getElementById('admDashContent');
  if (!el) return;

  const lowStock = d.lowStockProducts || [];
  const topProds = d.topProducts || [];

  el.innerHTML = `
    <!-- Stats cards -->
    <div class="adm-stats-grid">
      <div class="adm-stat-card">
        <div class="adm-stat-label">Total Revenue</div>
        <div class="adm-stat-value">${formatPrice(d.totalRevenuePaise || 0)}</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-label">Total Orders</div>
        <div class="adm-stat-value">${d.totalOrders || 0}</div>
      </div>
      <div class="adm-stat-card adm-stat-warn">
        <div class="adm-stat-label">Pending Action</div>
        <div class="adm-stat-value">${d.pendingOrders || 0}</div>
      </div>
      <div class="adm-stat-card">
        <div class="adm-stat-label">Shipped</div>
        <div class="adm-stat-value">${d.shippedOrders || 0}</div>
      </div>
    </div>

    <div class="adm-two-col">
      <!-- Top Products -->
      <div class="adm-card">
        <h2 class="adm-card-title">Top Products by Revenue</h2>
        ${topProds.length ? `
          <table class="adm-table">
            <thead><tr><th>Product</th><th>Revenue</th><th>Units</th></tr></thead>
            <tbody>
              ${topProds.map(p => `
                <tr>
                  <td>${_esc(p.name)}</td>
                  <td>${formatPrice(p.revenue)}</td>
                  <td>${p.units}</td>
                </tr>`).join('')}
            </tbody>
          </table>` : '<p class="adm-empty-msg">No orders yet.</p>'}
      </div>

      <!-- Low Stock -->
      <div class="adm-card">
        <h2 class="adm-card-title">Low Stock Alert
          ${lowStock.length ? `<span class="adm-badge-warn">${lowStock.length}</span>` : ''}
        </h2>
        ${lowStock.length ? `
          <table class="adm-table">
            <thead><tr><th>Product</th><th>SKU</th><th>Stock</th></tr></thead>
            <tbody>
              ${lowStock.map(p => `
                <tr class="${p.stockQty === 0 ? 'adm-row-danger' : ''}">
                  <td>${_esc(p.name)}</td>
                  <td class="adm-muted">${_esc(p.sku)}</td>
                  <td><strong>${p.stockQty}</strong></td>
                </tr>`).join('')}
            </tbody>
          </table>` : '<p class="adm-empty-msg">All products well-stocked.</p>'}
      </div>
    </div>`;
}

function _esc(s) {
  return s == null ? '' : String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

export function _shell(content) {
  return `
    <div class="adm-layout">
      <aside class="adm-sidebar">
        <div class="adm-sidebar-logo">
          <a href="#/" class="adm-logo">
            <img src="assets/images/saroori-logo.png" alt="Sasroori" height="32" style="display:block;flex-shrink:0" />
            <span>Saroori</span>
          </a>
          <span class="adm-sidebar-badge">Admin</span>
        </div>
        <nav class="adm-nav">
          <a href="#/admin" class="adm-nav-link" data-route="/admin">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
            <span>Dashboard</span>
          </a>
          <a href="#/admin/products" class="adm-nav-link" data-route="/admin/products">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>
            <span>Products</span>
          </a>
          <a href="#/admin/orders" class="adm-nav-link" data-route="/admin/orders">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>
            <span>Orders</span>
          </a>
          <a href="#/admin/users" class="adm-nav-link" data-route="/admin/users">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
            <span>Users</span>
          </a>
          <a href="#/admin/shipments" class="adm-nav-link" data-route="/admin/shipments">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="1" y="3" width="15" height="13"/><path d="M16 8h4l3 3v5h-7V8z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>
            <span>Shipments</span>
          </a>
          <a href="#/admin/invoices" class="adm-nav-link" data-route="/admin/invoices">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
            <span>Invoices</span>
          </a>
        </nav>
        <div class="adm-sidebar-footer">
          <a href="#/" class="adm-nav-link adm-back-link">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="15 18 9 12 15 6"/></svg>
            <span>Back to Shop</span>
          </a>
        </div>
      </aside>
      <main class="adm-main">
        ${content}
      </main>
    </div>`;
}
