/**
 * SASOORI — Order Detail & Tracking Page
 * Route: #/orders/:id  (requires auth)
 */

import { getOrder, cancelOrder } from '../api/orderApi.js';
import { formatPrice, formatDate, formatDateTime, formatOrderStatus, orderStatusBadgeClass } from '../utils/formatters.js';
import { showToast } from '../components/toast.js';
import store from '../store.js';

// ── Status step definitions ───────────────────────────────────────────────

const STATUS_STEPS = [
  { key: 'PENDING',           label: 'Order Placed' },
  { key: 'PAID',              label: 'Payment Confirmed' },
  { key: 'PROCESSING',        label: 'Being Packed' },
  { key: 'SHIPPED',           label: 'Shipped' },
  { key: 'DELIVERED',         label: 'Delivered' },
];

const STATUS_ORDER = ['PENDING', 'PAYMENT_INITIATED', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

// ── Module state ──────────────────────────────────────────────────────────

let _state = { order: null, loading: true, error: null };
let _orderId = null;

// ── Page object ───────────────────────────────────────────────────────────

const page = {
  meta: ({ params }) => ({ title: `Order ${params?.id?.slice(0, 8)?.toUpperCase() || ''}`, description: 'Track your order.' }),

  render(container) {
    container.innerHTML = `
      <div class="container orders-page">
        <a href="#/orders" class="ord-back-link">← My Orders</a>
        <div id="odError" class="co-error hidden"></div>
        <div id="odContent"></div>
      </div>`;
  },

  async onMount({ params } = {}) {
    if (!store.isLoggedIn()) {
      window.location.hash = '/login';
      return;
    }
    _orderId = params?.id;
    _state = { order: null, loading: true, error: null };
    _renderContent();
    await _load();
  },

  onDestroy() {
    _state = { order: null, loading: true, error: null };
    _orderId = null;
  },
};

export default page;

// ── Data loading ──────────────────────────────────────────────────────────

async function _load() {
  try {
    const data = await getOrder(_orderId);
    _state.order   = data.order || data.data || data;
    _state.error   = null;
  } catch (err) {
    _state.error = err.message || 'Failed to load order.';
  } finally {
    _state.loading = false;
    _renderContent();
  }
}

// ── Rendering ─────────────────────────────────────────────────────────────

function _renderContent() {
  const el = document.getElementById('odContent');
  const errEl = document.getElementById('odError');
  if (!el) return;

  if (_state.error) {
    errEl?.classList.remove('hidden');
    if (errEl) errEl.textContent = _state.error;
    el.innerHTML = '';
    return;
  }

  if (_state.loading) {
    el.innerHTML = `
      <div class="od-skeleton">
        <div class="skeleton" style="height:28px;width:50%;margin-bottom:var(--sp-4)"></div>
        <div class="skeleton" style="height:120px;margin-bottom:var(--sp-4)"></div>
        <div class="skeleton" style="height:200px"></div>
      </div>`;
    return;
  }

  const order = _state.order;
  if (!order) return;

  const items = order.items || order.orderItems || [];
  const addr  = _parseAddress(order.shippingAddress);
  const canCancel = order.status === 'PENDING' || order.status === 'PAID';
  const isCancelled = order.status === 'CANCELLED' || order.status === 'REFUNDED' || order.status === 'REFUND_INITIATED';
  const badgeClass = orderStatusBadgeClass(order.status);

  el.innerHTML = `
    <div class="od-layout">
      <!-- Left column -->
      <div class="od-left">

        <!-- Header -->
        <div class="od-header">
          <div>
            <h1 class="od-title">Order #${order.id.slice(0, 8).toUpperCase()}</h1>
            <p class="od-meta">Placed on ${formatDate(order.createdAt)}</p>
          </div>
          <span class="ord-badge ${badgeClass}">${formatOrderStatus(order.status)}</span>
        </div>

        <!-- Tracking timeline (not shown for cancelled) -->
        ${!isCancelled ? _trackingTimeline(order) : ''}

        <!-- Shipment info -->
        ${_shipmentInfo(order)}

        <!-- Items -->
        <div class="od-section">
          <h2 class="od-section-title">Items Ordered</h2>
          <div class="od-items">
            ${items.map(_itemRow).join('')}
          </div>
        </div>

        <!-- Cancel button -->
        ${canCancel ? `
          <div style="margin-top:var(--sp-4)">
            <button class="btn btn-sm btn-ghost btn-danger" id="odCancelBtn" data-id="${order.id}">
              Cancel Order
            </button>
          </div>` : ''}
      </div>

      <!-- Right column -->
      <div class="od-right">

        <!-- Price summary -->
        <div class="od-card">
          <h2 class="od-section-title">Price Details</h2>
          <div class="co-summary-row">
            <span>Subtotal</span>
            <span>${formatPrice(order.subtotalPaise)}</span>
          </div>
          <div class="co-summary-row">
            <span>Shipping</span>
            <span>${order.shippingPaise === 0 ? '<span class="co-free-shipping">FREE</span>' : formatPrice(order.shippingPaise)}</span>
          </div>
          ${order.discountPaise > 0 ? `
          <div class="co-summary-row">
            <span>Discount</span>
            <span style="color:var(--clr-success)">−${formatPrice(order.discountPaise)}</span>
          </div>` : ''}
          <div class="co-divider"></div>
          <div class="co-summary-row co-summary-total">
            <span>Total</span>
            <span>${formatPrice(order.totalPaise)}</span>
          </div>
        </div>

        <!-- Delivery address -->
        <div class="od-card" style="margin-top:var(--sp-4)">
          <h2 class="od-section-title">Delivery Address</h2>
          ${addr ? `
            <p class="od-addr-name">${addr.name}</p>
            <p class="od-addr-detail">${addr.line1}${addr.line2 ? ', ' + addr.line2 : ''}</p>
            <p class="od-addr-detail">${addr.city}, ${addr.state} — ${addr.pincode}</p>
            <p class="od-addr-detail">${addr.phone}</p>
          ` : '<p class="od-addr-detail">Address not available</p>'}
        </div>

        <!-- Notes -->
        ${order.notes ? `
        <div class="od-card" style="margin-top:var(--sp-4)">
          <h2 class="od-section-title">Notes</h2>
          <p class="od-addr-detail">${_esc(order.notes)}</p>
        </div>` : ''}

      </div>
    </div>`;

  _bindActions();
}

function _trackingTimeline(order) {
  const currentIdx = STATUS_ORDER.indexOf(order.status);

  return `
    <div class="od-section od-timeline">
      <h2 class="od-section-title">Order Status</h2>
      <div class="od-steps">
        ${STATUS_STEPS.map((step, i) => {
          const stepIdx = STATUS_ORDER.indexOf(step.key);
          const done    = currentIdx >= stepIdx;
          const current = STATUS_ORDER[currentIdx] === step.key;
          return `
            <div class="od-step ${done ? 'done' : ''} ${current ? 'current' : ''}">
              <div class="od-step-dot">${done ? '✓' : ''}</div>
              <div class="od-step-label">${step.label}</div>
            </div>`;
        }).join('')}
      </div>
    </div>`;
}

function _shipmentInfo(order) {
  const s = order.shipment;
  if (!s || !s.awbCode) return '';
  return `
    <div class="od-section">
      <h2 class="od-section-title">Shipment</h2>
      <div class="od-shipment">
        <div class="od-shipment-row">
          <span class="od-shipment-label">Courier</span>
          <span>${_esc(s.courierName || '—')}</span>
        </div>
        <div class="od-shipment-row">
          <span class="od-shipment-label">AWB / Tracking #</span>
          <span>
            ${_esc(s.awbCode)}
            <button class="btn-copy" data-copy="${_esc(s.awbCode)}" title="Copy">⧉</button>
          </span>
        </div>
        ${s.estimatedDelivery ? `
        <div class="od-shipment-row">
          <span class="od-shipment-label">Est. Delivery</span>
          <span>${formatDate(s.estimatedDelivery)}</span>
        </div>` : ''}
        ${s.trackingUrl ? `
        <div class="od-shipment-row">
          <a href="${s.trackingUrl}" target="_blank" rel="noopener" class="btn btn-sm btn-secondary">
            Track on Courier Site →
          </a>
        </div>` : ''}
      </div>
    </div>`;
}

function _itemRow(item) {
  const img = item.imageUrl || item.images?.[0] || '';
  return `
    <div class="od-item">
      ${img
        ? `<img src="${img}" alt="${_esc(item.productName || item.name)}" class="od-item-img" loading="lazy" />`
        : `<div class="od-item-img od-item-img-ph"></div>`}
      <div class="od-item-info">
        <p class="od-item-name">${_esc(item.productName || item.name)}</p>
        <p class="od-item-meta">SKU: ${_esc(item.productSku || item.sku || '—')} · Qty: ${item.quantity}</p>
      </div>
      <div class="od-item-price">${formatPrice(item.totalPaise)}</div>
    </div>`;
}

// ── Event binding ─────────────────────────────────────────────────────────

function _bindActions() {
  const cancelBtn = document.getElementById('odCancelBtn');
  if (cancelBtn) {
    cancelBtn.addEventListener('click', async () => {
      if (!confirm('Are you sure you want to cancel this order?')) return;
      cancelBtn.disabled = true;
      try {
        await cancelOrder(_orderId);
        showToast('success', 'Order cancelled', 'Your order has been cancelled.');
        await _load();
      } catch (err) {
        showToast('error', 'Cancel failed', err.message || 'Could not cancel order.');
        cancelBtn.disabled = false;
      }
    });
  }

  document.querySelectorAll('.btn-copy').forEach(btn => {
    btn.addEventListener('click', () => {
      navigator.clipboard?.writeText(btn.dataset.copy).catch(() => {});
      btn.textContent = '✓';
      setTimeout(() => { btn.textContent = '⧉'; }, 1500);
    });
  });
}

// ── Helpers ───────────────────────────────────────────────────────────────

function _parseAddress(raw) {
  if (!raw) return null;
  if (typeof raw === 'object') return raw;
  try { return JSON.parse(raw); } catch { return null; }
}

function _esc(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
