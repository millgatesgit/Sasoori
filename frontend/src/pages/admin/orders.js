/**
 * SASOORI — Admin Orders Page
 * Route: #/admin/orders  (requires ADMIN role)
 */

import { adminGetOrders, adminUpdateOrderStatus, adminGetOrderForInvoice } from '../../api/adminApi.js';
import { formatPrice, formatDate, formatOrderStatus, orderStatusBadgeClass } from '../../utils/formatters.js';
import { showToast } from '../../components/toast.js';
import { _shell } from './dashboard.js';

const STATUSES = ['', 'PENDING', 'PAYMENT_INITIATED', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'];

let _state = {
  orders: [], total: 0, page: 1, size: 20,
  filterStatus: '',
  loading: false,
  detailOrderId: null, detailOrder: null, detailLoading: false,
};

const page = {
  meta: () => ({ title: 'Admin — Orders', description: '' }),

  render(container) {
    container.innerHTML = _shell(`
      <div class="adm-page-header">
        <h1 class="adm-page-title">Orders</h1>
        <select class="form-input adm-filter-select" id="admOrdStatusFilter">
          ${STATUSES.map(s => `<option value="${s}">${s || 'All Statuses'}</option>`).join('')}
        </select>
      </div>
      <div class="co-error hidden" id="admOrdError"></div>
      <div id="admOrdList"></div>
      <div id="admOrdPaging" class="ord-paging hidden"></div>
      <div id="admOrdPanel"></div>
    `);
    _markActiveNav('/admin/orders');
  },

  async onMount() {
    document.getElementById('admOrdStatusFilter')?.addEventListener('change', e => {
      _state.filterStatus = e.target.value;
      _state.page = 1;
      _load();
    });
    await _load();
  },

  onDestroy() {
    _state = { orders: [], total: 0, page: 1, size: 20, filterStatus: '', loading: false, detailOrderId: null, detailOrder: null, detailLoading: false };
  },
};

export default page;

async function _load() {
  _state.loading = true;
  _renderList();
  try {
    const data = await adminGetOrders({ status: _state.filterStatus || undefined, page: _state.page, size: _state.size });
    const d = data.data || data;
    _state.orders = d.orders || [];
    _state.total  = d.total  || 0;
  } catch (err) {
    const el = document.getElementById('admOrdError');
    if (el) { el.textContent = err.message || 'Failed to load orders.'; el.classList.remove('hidden'); }
  } finally {
    _state.loading = false;
    _renderList();
    _renderPaging();
  }
}

function _renderList() {
  const el = document.getElementById('admOrdList');
  if (!el) return;

  if (_state.loading) { el.innerHTML = '<div class="adm-loading">Loading…</div>'; return; }

  if (!_state.orders.length) {
    el.innerHTML = '<p class="adm-empty-msg">No orders found.</p>';
    return;
  }

  el.innerHTML = `
    <table class="adm-table adm-table-full">
      <thead>
        <tr><th>Order ID</th><th>Date</th><th>Total</th><th>Status</th><th>Change Status</th><th>Actions</th></tr>
      </thead>
      <tbody>
        ${_state.orders.map(o => `
          <tr>
            <td><a href="#/orders/${o.id}" class="adm-link">${o.id.slice(0, 8).toUpperCase()}</a></td>
            <td class="adm-muted">${formatDate(o.createdAt)}</td>
            <td>${formatPrice(o.totalPaise)}</td>
            <td><span class="ord-badge ${orderStatusBadgeClass(o.status)}">${formatOrderStatus(o.status)}</span></td>
            <td>
              <select class="form-input adm-status-select" data-id="${o.id}" data-current="${o.status}">
                ${STATUSES.filter(s => s).map(s =>
                  `<option value="${s}" ${s === o.status ? 'selected' : ''}>${formatOrderStatus(s)}</option>`
                ).join('')}
              </select>
            </td>
            <td>
              <button class="btn btn-sm btn-ghost adm-view-btn" data-id="${o.id}">View</button>
            </td>
          </tr>`).join('')}
      </tbody>
    </table>`;

  el.querySelectorAll('.adm-status-select').forEach(sel => {
    sel.addEventListener('change', async () => {
      const id      = sel.dataset.id;
      const current = sel.dataset.current;
      const newStat = sel.value;
      if (newStat === current) return;
      if (!confirm(`Change order status to "${formatOrderStatus(newStat)}"?`)) {
        sel.value = current;
        return;
      }
      sel.disabled = true;
      try {
        await adminUpdateOrderStatus(id, newStat);
        showToast('success', 'Updated', `Order status updated to ${formatOrderStatus(newStat)}.`);
        sel.dataset.current = newStat;
      } catch (err) {
        showToast('error', 'Error', err.message || 'Status update failed.');
        sel.value = current;
      } finally {
        sel.disabled = false;
      }
    });
  });

  el.querySelectorAll('.adm-view-btn').forEach(btn => {
    btn.addEventListener('click', () => _openDetail(btn.dataset.id));
  });
}

async function _openDetail(orderId) {
  _state.detailOrderId = orderId;
  _state.detailOrder = null;
  _state.detailLoading = true;
  _renderPanel();
  try {
    const data = await adminGetOrderForInvoice(orderId);
    _state.detailOrder = data.data || data;
    _state.detailLoading = false;
    _renderPanel();
  } catch (err) {
    _state.detailLoading = false;
    _state.detailOrderId = null;
    showToast('error', 'Error', err.message || 'Could not load order.');
    _renderPanel();
  }
}

function _closeDetail() {
  _state.detailOrderId = null;
  _state.detailOrder = null;
  _renderPanel();
}

function _renderPanel() {
  const el = document.getElementById('admOrdPanel');
  if (!el) return;

  if (!_state.detailOrderId) { el.innerHTML = ''; return; }

  const o = _state.detailOrder;
  const orderId = _state.detailOrderId;
  const shortId = orderId.slice(0, 8).toUpperCase();

  if (_state.detailLoading || !o) {
    el.innerHTML = `
      <div class="adm-panel-overlay">
        <div class="adm-panel">
          <div class="adm-panel-header">
            <span class="adm-panel-title">Order #${shortId}</span>
            <button class="adm-panel-close" id="admOrdPanelClose">✕</button>
          </div>
          <div class="adm-panel-body"><div class="adm-loading">Loading…</div></div>
        </div>
      </div>`;
    document.getElementById('admOrdPanelClose')?.addEventListener('click', _closeDetail);
    return;
  }

  const items = o.items || [];
  const addr  = o.shippingAddress || {};
  const STATUS_FLOW = ['PENDING','PAYMENT_INITIATED','PAID','PROCESSING','SHIPPED','DELIVERED'];
  const currentIdx = STATUS_FLOW.indexOf(o.status);

  el.innerHTML = `
    <div class="adm-panel-overlay" id="admOrdOverlay">
      <div class="adm-panel">
        <div class="adm-panel-header">
          <span class="adm-panel-title">Order #${shortId}</span>
          <button class="adm-panel-close" id="admOrdPanelClose">✕</button>
        </div>
        <div class="adm-panel-body">

          <!-- Status badge -->
          <div style="margin-bottom:var(--sp-5)">
            <span class="ord-badge ${orderStatusBadgeClass(o.status)}">${formatOrderStatus(o.status)}</span>
            <span style="font-size:var(--ts-xs);color:var(--clr-text-m);margin-left:var(--sp-2)">${formatDate(o.createdAt)}</span>
          </div>

          <!-- Order items -->
          <h4 style="font-size:var(--ts-sm);font-weight:600;margin-bottom:var(--sp-3);color:var(--clr-text-m);text-transform:uppercase;letter-spacing:.05em">Items</h4>
          <div style="border:1px solid var(--clr-border);border-radius:var(--r-md);overflow:hidden;margin-bottom:var(--sp-5)">
            ${items.map(i => `
              <div style="display:flex;justify-content:space-between;align-items:center;padding:var(--sp-3) var(--sp-4);border-bottom:1px solid var(--clr-divider)">
                <div>
                  <div style="font-size:var(--ts-sm);font-weight:500">${_esc(i.productName || i.product_name || '')}</div>
                  <div style="font-size:var(--ts-xs);color:var(--clr-text-m)">SKU: ${_esc(i.productSku || i.product_sku || '')} × ${i.quantity}</div>
                </div>
                <div style="font-weight:600;font-size:var(--ts-sm)">${formatPrice(i.totalPaise || i.total_paise || 0)}</div>
              </div>`).join('')}
            <div style="display:flex;justify-content:space-between;padding:var(--sp-3) var(--sp-4);background:var(--clr-bg)">
              <span style="font-size:var(--ts-sm);color:var(--clr-text-m)">Shipping</span>
              <span style="font-size:var(--ts-sm)">${o.shippingPaise ? formatPrice(o.shippingPaise) : 'FREE'}</span>
            </div>
            <div style="display:flex;justify-content:space-between;padding:var(--sp-3) var(--sp-4);background:var(--clr-bg);border-top:1px solid var(--clr-border)">
              <span style="font-weight:600">Total</span>
              <span style="font-weight:700;color:var(--clr-primary)">${formatPrice(o.totalPaise || o.total_paise || 0)}</span>
            </div>
          </div>

          <!-- Shipping address -->
          <h4 style="font-size:var(--ts-sm);font-weight:600;margin-bottom:var(--sp-3);color:var(--clr-text-m);text-transform:uppercase;letter-spacing:.05em">Delivery Address</h4>
          <div style="background:var(--clr-bg);border-radius:var(--r-md);padding:var(--sp-4);font-size:var(--ts-sm);margin-bottom:var(--sp-5)">
            <div style="font-weight:500">${_esc(addr.name || '')}</div>
            <div style="color:var(--clr-text-m)">${_esc(addr.phone || '')}</div>
            <div style="color:var(--clr-text-m)">${_esc(addr.line1 || '')}${addr.line2 ? ', ' + _esc(addr.line2) : ''}</div>
            <div style="color:var(--clr-text-m)">${_esc(addr.city || '')}, ${_esc(addr.state || '')} – ${_esc(addr.pincode || '')}</div>
          </div>

          <!-- Status timeline -->
          <h4 style="font-size:var(--ts-sm);font-weight:600;margin-bottom:var(--sp-3);color:var(--clr-text-m);text-transform:uppercase;letter-spacing:.05em">Timeline</h4>
          <ul class="adm-timeline">
            ${STATUS_FLOW.map((s, i) => {
              const done   = i < currentIdx;
              const active = i === currentIdx;
              return `<li class="adm-timeline-item">
                <div class="adm-timeline-dot ${done ? 'done' : active ? 'active' : ''}">${done ? '✓' : ''}</div>
                <div>
                  <div class="adm-timeline-label">${formatOrderStatus(s)}</div>
                  ${active ? `<div class="adm-timeline-time">Current status</div>` : ''}
                </div>
              </li>`;
            }).join('')}
          </ul>

        </div>
        <div class="adm-panel-footer">
          <button class="btn btn-secondary" id="admOrdPanelClose2">Close</button>
        </div>
      </div>
    </div>`;

  document.getElementById('admOrdPanelClose')?.addEventListener('click', _closeDetail);
  document.getElementById('admOrdPanelClose2')?.addEventListener('click', _closeDetail);
  document.getElementById('admOrdOverlay')?.addEventListener('click', e => {
    if (e.target.id === 'admOrdOverlay') _closeDetail();
  });
}

function _renderPaging() {
  const el = document.getElementById('admOrdPaging');
  if (!el) return;
  const totalPages = Math.ceil(_state.total / _state.size);
  if (totalPages <= 1) { el.classList.add('hidden'); return; }
  el.classList.remove('hidden');
  el.innerHTML = `
    <button class="btn btn-sm btn-secondary" id="admOPrev" ${_state.page <= 1 ? 'disabled' : ''}>← Prev</button>
    <span class="ord-page-info">Page ${_state.page} of ${totalPages}</span>
    <button class="btn btn-sm btn-secondary" id="admONext" ${_state.page >= totalPages ? 'disabled' : ''}>Next →</button>`;
  document.getElementById('admOPrev')?.addEventListener('click', () => { _state.page--; _load(); });
  document.getElementById('admONext')?.addEventListener('click', () => { _state.page++; _load(); });
}

function _esc(s) {
  return s == null ? '' : String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function _markActiveNav(route) {
  document.querySelectorAll('.adm-nav-link').forEach(l => {
    l.classList.toggle('active', l.dataset.route === route);
  });
}
