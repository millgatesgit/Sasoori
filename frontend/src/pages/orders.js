/**
 * SASOORI — Orders History Page
 * Route: #/orders  (requires auth)
 */

import { getOrders, cancelOrder } from '../api/orderApi.js';
import { formatPrice, formatDate, formatOrderStatus, orderStatusBadgeClass } from '../utils/formatters.js';
import { showToast } from '../components/toast.js';
import store from '../store.js';

// ── Module state ──────────────────────────────────────────────────────────

let _state = {
  orders:   [],
  page:     1,
  size:     10,
  total:    0,
  loading:  false,
  error:    null,
};

// ── Page object ───────────────────────────────────────────────────────────

const page = {
  meta: () => ({ title: 'My Orders', description: 'View your order history.' }),

  render(container) {
    container.innerHTML = `
      <div class="container orders-page">
        <h1 class="ord-page-title">My Orders</h1>
        <div id="ordError"  class="co-error hidden"></div>
        <div id="ordList"   class="ord-list"></div>
        <div id="ordPaging" class="ord-paging hidden"></div>
      </div>`;
  },

  async onMount() {
    if (!store.isLoggedIn()) {
      window.location.hash = '/login';
      return;
    }
    _state = { orders: [], page: 1, size: 10, total: 0, loading: false, error: null };
    await _load();
  },

  onDestroy() {
    _state = { orders: [], page: 1, size: 10, total: 0, loading: false, error: null };
  },
};

export default page;

// ── Data loading ──────────────────────────────────────────────────────────

async function _load() {
  _state.loading = true;
  _renderList();
  try {
    const data = await getOrders(_state.page, _state.size);
    _state.orders = data.orders || data.data?.orders || [];
    _state.total  = data.total  || data.data?.total  || 0;
    _state.error  = null;
  } catch (err) {
    _state.error = err.message || 'Failed to load orders.';
  } finally {
    _state.loading = false;
    _renderList();
    _renderPaging();
  }
}

// ── Rendering ─────────────────────────────────────────────────────────────

function _renderList() {
  const el = document.getElementById('ordList');
  const errEl = document.getElementById('ordError');
  if (!el) return;

  if (_state.error) {
    errEl?.classList.remove('hidden');
    if (errEl) errEl.textContent = _state.error;
  } else {
    errEl?.classList.add('hidden');
  }

  if (_state.loading) {
    el.innerHTML = _skeleton();
    return;
  }

  if (!_state.orders.length) {
    el.innerHTML = `
      <div class="ord-empty">
        <div class="ord-empty-icon">📦</div>
        <h2 class="ord-empty-title">No orders yet</h2>
        <p class="ord-empty-desc">Your order history will appear here once you place your first order.</p>
        <a href="#/products" class="btn btn-primary" style="margin-top:var(--sp-6);display:inline-block">Start Shopping</a>
      </div>`;
    return;
  }

  el.innerHTML = _state.orders.map(_orderCard).join('');
  _bindActions();
}

function _orderCard(order) {
  const badgeClass = orderStatusBadgeClass(order.status);
  const statusLabel = formatOrderStatus(order.status);
  const items = order.items || order.orderItems || [];
  const preview = items.slice(0, 2).map(i => i.productName || i.name).join(', ');
  const more = items.length > 2 ? ` +${items.length - 2} more` : '';
  const canCancel = order.status === 'PENDING' || order.status === 'PAID';

  return `
    <div class="ord-card" data-order-id="${order.id}">
      <div class="ord-card-header">
        <div>
          <span class="ord-id">Order #${order.id.slice(0, 8).toUpperCase()}</span>
          <span class="ord-date">${formatDate(order.createdAt)}</span>
        </div>
        <div class="ord-status-wrap">
          <span class="ord-badge ${badgeClass}">${statusLabel}</span>
          <span class="ord-total">${formatPrice(order.totalPaise)}</span>
        </div>
      </div>
      <div class="ord-card-body">
        <p class="ord-items-preview">${preview}${more}</p>
      </div>
      <div class="ord-card-footer">
        <a href="#/orders/${order.id}" class="btn btn-sm btn-secondary">View Details</a>
        ${order.status === 'SHIPPED' || order.status === 'DELIVERED'
          ? `<a href="#/orders/${order.id}" class="btn btn-sm btn-ghost">Track Order →</a>`
          : ''}
        ${canCancel
          ? `<button class="btn btn-sm btn-ghost btn-danger ord-cancel-btn" data-id="${order.id}">Cancel</button>`
          : ''}
      </div>
    </div>`;
}

function _skeleton() {
  return Array(3).fill(0).map(() => `
    <div class="ord-card ord-card-skeleton">
      <div class="skeleton" style="height:20px;width:60%;margin-bottom:var(--sp-3)"></div>
      <div class="skeleton" style="height:16px;width:80%;margin-bottom:var(--sp-2)"></div>
      <div class="skeleton" style="height:14px;width:40%"></div>
    </div>`).join('');
}

function _renderPaging() {
  const el = document.getElementById('ordPaging');
  if (!el) return;
  const totalPages = Math.ceil(_state.total / _state.size);
  if (totalPages <= 1) { el.classList.add('hidden'); return; }

  el.classList.remove('hidden');
  el.innerHTML = `
    <button class="btn btn-sm btn-secondary" id="ordPrevBtn" ${_state.page <= 1 ? 'disabled' : ''}>← Prev</button>
    <span class="ord-page-info">Page ${_state.page} of ${totalPages}</span>
    <button class="btn btn-sm btn-secondary" id="ordNextBtn" ${_state.page >= totalPages ? 'disabled' : ''}>Next →</button>`;

  document.getElementById('ordPrevBtn')?.addEventListener('click', () => { _state.page--; _load(); });
  document.getElementById('ordNextBtn')?.addEventListener('click', () => { _state.page++; _load(); });
}

// ── Event binding ─────────────────────────────────────────────────────────

function _bindActions() {
  document.querySelectorAll('.ord-cancel-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.id;
      if (!confirm('Cancel this order?')) return;
      btn.disabled = true;
      try {
        await cancelOrder(id);
        showToast('success', 'Order cancelled', 'Your order has been cancelled.');
        await _load();
      } catch (err) {
        showToast('error', 'Cancel failed', err.message || 'Could not cancel order.');
        btn.disabled = false;
      }
    });
  });
}
