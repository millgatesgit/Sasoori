/**
 * SASOORI — Admin Invoices Page
 * Route: #/admin/invoices  (requires ADMIN role)
 */

import { adminGetInvoices, adminGetOrderForInvoice } from '../../api/adminApi.js';
import { formatPrice, formatDate } from '../../utils/formatters.js';
import { showToast } from '../../components/toast.js';
import { _shell } from './dashboard.js';

let _state = {
  invoices: [], total: 0, page: 1, size: 20,
  loading: false,
  previewOrderId: null,
  previewOrder: null,
  previewLoading: false,
};

const page = {
  meta: () => ({ title: 'Admin — Invoices', description: '' }),

  render(container) {
    container.innerHTML = _shell(`
      <div class="adm-page-header">
        <h1 class="adm-page-title">Invoices</h1>
      </div>
      <div class="co-error hidden" id="admInvError"></div>
      <div id="admInvList"></div>
      <div id="admInvPaging" class="ord-paging hidden"></div>
      <div id="admInvPanel"></div>
    `);
    _markActiveNav('/admin/invoices');
  },

  async onMount() {
    await _load();
  },

  onDestroy() {
    _state = {
      invoices: [], total: 0, page: 1, size: 20,
      loading: false,
      previewOrderId: null,
      previewOrder: null,
      previewLoading: false,
    };
  },
};

export default page;

async function _load() {
  _state.loading = true;
  _renderList();
  try {
    const data = await adminGetInvoices({ page: _state.page, size: _state.size });
    const d = data.data || data;
    _state.invoices = d.invoices || d.orders || [];
    _state.total    = d.total    || 0;
  } catch (err) {
    const el = document.getElementById('admInvError');
    if (el) { el.textContent = err.message || 'Failed to load invoices.'; el.classList.remove('hidden'); }
  } finally {
    _state.loading = false;
    _renderList();
    _renderPaging();
  }
}

function _renderList() {
  const el = document.getElementById('admInvList');
  if (!el) return;

  if (_state.loading) { el.innerHTML = '<div class="adm-loading">Loading…</div>'; return; }

  if (!_state.invoices.length) {
    el.innerHTML = '<p class="adm-empty-msg">No invoices found.</p>';
    return;
  }

  el.innerHTML = `
    <table class="adm-table adm-table-full">
      <thead>
        <tr>
          <th>Invoice #</th>
          <th>Order ID</th>
          <th>Customer Name</th>
          <th>Date</th>
          <th>Total</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${_state.invoices.map(inv => {
          const invNum   = 'INV-' + inv.id.slice(0, 8).toUpperCase();
          const custName = inv.shippingAddress?.name || inv.customerName || '—';
          return `
            <tr>
              <td><strong>${_esc(invNum)}</strong></td>
              <td><a href="#/orders/${inv.id}" class="adm-link">${inv.id.slice(0, 8).toUpperCase()}</a></td>
              <td>${_esc(custName)}</td>
              <td class="adm-muted">${inv.createdAt ? formatDate(inv.createdAt) : '—'}</td>
              <td>${formatPrice(inv.totalPaise || 0)}</td>
              <td class="adm-actions-cell">
                <button class="btn btn-sm btn-secondary adm-inv-preview-btn" data-orderid="${inv.id}">👁 Preview</button>
                <button class="btn btn-sm btn-secondary adm-inv-print-btn"   data-orderid="${inv.id}">🖨 Print</button>
              </td>
            </tr>`;
        }).join('')}
      </tbody>
    </table>`;

  el.querySelectorAll('.adm-inv-preview-btn').forEach(btn => {
    btn.addEventListener('click', () => _openPreview(btn.dataset.orderid));
  });

  el.querySelectorAll('.adm-inv-print-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      await _openPreview(btn.dataset.orderid);
      // Small delay so panel renders before print dialog
      setTimeout(() => window.print(), 300);
    });
  });
}

async function _openPreview(orderId) {
  _state.previewOrderId = orderId;
  _state.previewOrder   = null;
  _state.previewLoading = true;
  _renderPanel();

  try {
    const data = await adminGetOrderForInvoice(orderId);
    _state.previewOrder = data.data || data;
  } catch (err) {
    showToast('error', 'Error', err.message || 'Failed to load order details.');
    _state.previewOrder   = null;
    _state.previewOrderId = null;
  } finally {
    _state.previewLoading = false;
    _renderPanel();
  }
}

function _closePreview() {
  _state.previewOrderId = null;
  _state.previewOrder   = null;
  _state.previewLoading = false;
  _renderPanel();
}

function _renderPanel() {
  const el = document.getElementById('admInvPanel');
  if (!el) return;

  if (!_state.previewOrderId) {
    el.innerHTML = '';
    return;
  }

  const overlayInner = _state.previewLoading
    ? '<div class="adm-loading" style="padding:var(--sp-6)">Loading invoice…</div>'
    : _buildInvoiceHTML(_state.previewOrder, _state.previewOrderId);

  el.innerHTML = `
    <div class="adm-panel-overlay" id="admInvOverlay">
      <div class="adm-panel">
        <div class="adm-panel-header">
          <span class="adm-panel-title">Invoice Preview</span>
          <button class="adm-panel-close" id="admInvClose" aria-label="Close">✕</button>
        </div>
        <div class="adm-panel-body">
          ${overlayInner}
          ${!_state.previewLoading ? `
            <div style="margin-top:var(--sp-4);display:flex;gap:var(--sp-3)">
              <button class="btn btn-primary" id="admInvPrintBtn">🖨 Print Invoice</button>
              <button class="btn btn-secondary" id="admInvCloseBtn">Close</button>
            </div>` : ''}
        </div>
      </div>
    </div>`;

  document.getElementById('admInvClose')?.addEventListener('click', _closePreview);
  document.getElementById('admInvCloseBtn')?.addEventListener('click', _closePreview);
  document.getElementById('admInvOverlay')?.addEventListener('click', e => {
    if (e.target === e.currentTarget) _closePreview();
  });
  document.getElementById('admInvPrintBtn')?.addEventListener('click', () => window.print());
}

function _buildInvoiceHTML(order, orderId) {
  if (!order) return '<p class="adm-empty-msg">Order data unavailable.</p>';

  const invNum   = 'INV-' + orderId.slice(0, 8).toUpperCase();
  const date     = order.createdAt ? formatDate(order.createdAt) : '—';
  const addr     = order.shippingAddress || {};
  const custName = addr.name || order.customerName || '—';
  const items    = order.items || order.orderItems || [];

  const itemsHTML = items.length
    ? `
      <table style="width:100%;border-collapse:collapse;margin-top:var(--sp-4)">
        <thead>
          <tr style="border-bottom:2px solid #e5e5e5">
            <th style="text-align:left;padding:var(--sp-2) var(--sp-3);font-size:0.8rem;color:#888">Item</th>
            <th style="text-align:center;padding:var(--sp-2) var(--sp-3);font-size:0.8rem;color:#888">Qty</th>
            <th style="text-align:right;padding:var(--sp-2) var(--sp-3);font-size:0.8rem;color:#888">Unit Price</th>
            <th style="text-align:right;padding:var(--sp-2) var(--sp-3);font-size:0.8rem;color:#888">Amount</th>
          </tr>
        </thead>
        <tbody>
          ${items.map(item => {
            const unitPaise   = item.pricePaise  || item.unitPricePaise || 0;
            const qty         = item.quantity     || 1;
            const totalPaise  = item.totalPaise   || (unitPaise * qty);
            return `
              <tr style="border-bottom:1px solid #f0f0f0">
                <td style="padding:var(--sp-2) var(--sp-3);font-size:0.875rem">${_esc(item.productName || item.name || '—')}</td>
                <td style="padding:var(--sp-2) var(--sp-3);font-size:0.875rem;text-align:center">${qty}</td>
                <td style="padding:var(--sp-2) var(--sp-3);font-size:0.875rem;text-align:right">${formatPrice(unitPaise)}</td>
                <td style="padding:var(--sp-2) var(--sp-3);font-size:0.875rem;text-align:right">${formatPrice(totalPaise)}</td>
              </tr>`;
          }).join('')}
        </tbody>
      </table>`
    : '<p style="color:#888;font-size:0.875rem">No items found.</p>';

  const subtotalPaise  = order.subtotalPaise  || order.totalPaise || 0;
  const shippingPaise  = order.shippingPaise  || 0;
  const discountPaise  = order.discountPaise  || 0;
  const totalPaise     = order.totalPaise     || 0;

  return `
    <div class="adm-invoice-preview" id="invoicePrintArea">
      <div class="adm-invoice-header">
        <div>
          <div style="font-size:1.5rem;font-weight:700;color:#2E7D32">🌿 Sasoori</div>
          <div style="font-size:0.75rem;color:#888;font-style:italic">"From Our Home to Your Home"</div>
        </div>
        <div style="text-align:right">
          <div style="font-size:1.1rem;font-weight:700">INVOICE</div>
          <div style="font-size:0.8rem;color:#888">#${_esc(invNum)}</div>
          <div style="font-size:0.8rem;color:#888">${_esc(date)}</div>
        </div>
      </div>

      <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--sp-4);margin-top:var(--sp-5)">
        <div>
          <div style="font-size:0.7rem;font-weight:700;text-transform:uppercase;color:#888;letter-spacing:0.05em;margin-bottom:var(--sp-1)">Bill To</div>
          <div style="font-size:0.9rem;font-weight:600">${_esc(custName)}</div>
          ${addr.line1    ? `<div style="font-size:0.8rem;color:#555">${_esc(addr.line1)}</div>` : ''}
          ${addr.line2    ? `<div style="font-size:0.8rem;color:#555">${_esc(addr.line2)}</div>` : ''}
          ${addr.city || addr.state ? `<div style="font-size:0.8rem;color:#555">${_esc([addr.city, addr.state, addr.pincode].filter(Boolean).join(', '))}</div>` : ''}
          ${addr.phone    ? `<div style="font-size:0.8rem;color:#555">Ph: ${_esc(addr.phone)}</div>` : ''}
        </div>
        <div style="text-align:right">
          <div style="font-size:0.7rem;font-weight:700;text-transform:uppercase;color:#888;letter-spacing:0.05em;margin-bottom:var(--sp-1)">Order Info</div>
          <div style="font-size:0.8rem;color:#555">Order: ${orderId.slice(0, 8).toUpperCase()}</div>
          <div style="font-size:0.8rem;color:#555">Date: ${_esc(date)}</div>
          ${order.status  ? `<div style="font-size:0.8rem;color:#555">Status: ${_esc(order.status)}</div>` : ''}
          ${order.paymentMethod ? `<div style="font-size:0.8rem;color:#555">Payment: ${_esc(order.paymentMethod)}</div>` : ''}
        </div>
      </div>

      ${itemsHTML}

      <div style="margin-top:var(--sp-4);border-top:2px solid #e5e5e5;padding-top:var(--sp-3)">
        <div style="display:flex;justify-content:flex-end">
          <div style="min-width:220px">
            <div style="display:flex;justify-content:space-between;font-size:0.85rem;padding:var(--sp-1) 0;color:#555">
              <span>Subtotal</span>
              <span>${formatPrice(subtotalPaise)}</span>
            </div>
            ${shippingPaise > 0 ? `
            <div style="display:flex;justify-content:space-between;font-size:0.85rem;padding:var(--sp-1) 0;color:#555">
              <span>Shipping</span>
              <span>${formatPrice(shippingPaise)}</span>
            </div>` : ''}
            ${discountPaise > 0 ? `
            <div style="display:flex;justify-content:space-between;font-size:0.85rem;padding:var(--sp-1) 0;color:#2E7D32">
              <span>Discount</span>
              <span>− ${formatPrice(discountPaise)}</span>
            </div>` : ''}
            <div style="display:flex;justify-content:space-between;font-size:1rem;font-weight:700;padding:var(--sp-2) 0;border-top:1px solid #e5e5e5;margin-top:var(--sp-1)">
              <span>Total</span>
              <span style="color:#2E7D32">${formatPrice(totalPaise)}</span>
            </div>
          </div>
        </div>
      </div>

      <div style="margin-top:var(--sp-6);padding-top:var(--sp-3);border-top:1px solid #f0f0f0;font-size:0.75rem;color:#aaa;text-align:center">
        Thank you for shopping with Sasoori! For queries: support@sasoori.in
      </div>
    </div>`;
}

function _renderPaging() {
  const el = document.getElementById('admInvPaging');
  if (!el) return;
  const totalPages = Math.ceil(_state.total / _state.size);
  if (totalPages <= 1) { el.classList.add('hidden'); return; }
  el.classList.remove('hidden');
  el.innerHTML = `
    <button class="btn btn-sm btn-secondary" id="admIPrev" ${_state.page <= 1 ? 'disabled' : ''}>← Prev</button>
    <span class="ord-page-info">Page ${_state.page} of ${totalPages}</span>
    <button class="btn btn-sm btn-secondary" id="admINext" ${_state.page >= totalPages ? 'disabled' : ''}>Next →</button>`;
  document.getElementById('admIPrev')?.addEventListener('click', () => { _state.page--; _load(); });
  document.getElementById('admINext')?.addEventListener('click', () => { _state.page++; _load(); });
}

function _markActiveNav(route) {
  document.querySelectorAll('.adm-nav-link').forEach(l => {
    l.classList.toggle('active', l.dataset.route === route);
  });
}

function _esc(s) {
  return s == null ? '' : String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
