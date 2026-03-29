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
          <a href="#/" class="adm-logo">🌿 Sasoori</a>
        </div>
        <nav class="adm-nav">
          <a href="#/admin"          class="adm-nav-link" data-route="/admin">📊 Dashboard</a>
          <a href="#/admin/products" class="adm-nav-link" data-route="/admin/products">📦 Products</a>
          <a href="#/admin/orders"   class="adm-nav-link" data-route="/admin/orders">🛒 Orders</a>
          <a href="#/admin/users"    class="adm-nav-link" data-route="/admin/users">👥 Users</a>
          <a href="#/admin/shipments" class="adm-nav-link" data-route="/admin/shipments">🚚 Shipments</a>
          <a href="#/admin/invoices"  class="adm-nav-link" data-route="/admin/invoices">🧾 Invoices</a>
        </nav>
        <a href="#/" class="adm-nav-link adm-logout">← Back to Shop</a>
      </aside>
      <main class="adm-main">
        ${content}
      </main>
    </div>`;
}
