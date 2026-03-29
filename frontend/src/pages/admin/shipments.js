/**
 * SASOORI — Admin Shipments Page
 * Route: #/admin/shipments  (requires ADMIN role)
 */

import { adminGetShipments, adminCreateShipment, adminTrackShipment } from '../../api/adminApi.js';
import { formatPrice, formatDate } from '../../utils/formatters.js';
import { showToast } from '../../components/toast.js';
import { _shell } from './dashboard.js';

const SHIP_STATUSES = ['', 'NEW', 'PICKUP_SCHEDULED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'UNDELIVERED'];

let _state = {
  shipments: [], total: 0, page: 1, size: 20,
  filterStatus: '',
  loading: false,
  trackingOpen: null,
  trackingData: null,
  trackingLoading: false,
};

const page = {
  meta: () => ({ title: 'Admin — Shipments', description: '' }),

  render(container) {
    container.innerHTML = _shell(`
      <div class="adm-page-header">
        <h1 class="adm-page-title">Shipments</h1>
        <select class="form-input adm-filter-select" id="admShipStatusFilter">
          ${SHIP_STATUSES.map(s => `<option value="${s}">${s || 'All Statuses'}</option>`).join('')}
        </select>
      </div>
      <div style="display:flex;align-items:center;gap:var(--sp-2);font-size:var(--ts-xs);color:var(--clr-text-m);margin-bottom:var(--sp-4)">
        <span style="width:8px;height:8px;border-radius:50%;background:var(--clr-success);display:inline-block"></span>
        Shiprocket API Connected
      </div>
      <div class="co-error hidden" id="admShipError"></div>
      <div id="admShipList"></div>
      <div id="admShipPaging" class="ord-paging hidden"></div>
    `);
    _markActiveNav('/admin/shipments');
  },

  async onMount() {
    document.getElementById('admShipStatusFilter')?.addEventListener('change', e => {
      _state.filterStatus = e.target.value;
      _state.page = 1;
      _load();
    });
    await _load();
  },

  onDestroy() {
    _state = {
      shipments: [], total: 0, page: 1, size: 20,
      filterStatus: '',
      loading: false,
      trackingOpen: null,
      trackingData: null,
      trackingLoading: false,
    };
  },
};

export default page;

async function _load() {
  _state.loading = true;
  _renderList();
  try {
    const data = await adminGetShipments({ status: _state.filterStatus || undefined, page: _state.page, size: _state.size });
    const d = data.data || data;
    _state.shipments = d.shipments || [];
    _state.total     = d.total     || 0;
  } catch (err) {
    const el = document.getElementById('admShipError');
    if (el) { el.textContent = err.message || 'Failed to load shipments.'; el.classList.remove('hidden'); }
  } finally {
    _state.loading = false;
    _renderList();
    _renderPaging();
  }
}

function _renderList() {
  const el = document.getElementById('admShipList');
  if (!el) return;

  if (_state.loading) { el.innerHTML = '<div class="adm-loading">Loading…</div>'; return; }

  if (!_state.shipments.length) {
    el.innerHTML = '<p class="adm-empty-msg">No shipments found.</p>';
    return;
  }

  const rows = _state.shipments.map(s => {
    const mainRow = `
      <tr>
        <td><a href="#/orders/${s.orderId}" class="adm-link">${s.orderId.slice(0, 8).toUpperCase()}</a></td>
        <td class="adm-muted">${_esc(s.awbCode || '—')}</td>
        <td class="adm-muted">${_esc(s.courier || '—')}</td>
        <td class="adm-muted">${s.bookedAt ? formatDate(s.bookedAt) : '—'}</td>
        <td class="adm-muted">${s.edd ? formatDate(s.edd) : '—'}</td>
        <td><span class="ord-badge badge-${_shipBadgeClass(s.status)}">${_esc(s.status || '—')}</span></td>
        <td class="adm-actions-cell">
          <button class="btn btn-sm btn-secondary adm-track-btn" data-orderid="${s.orderId}">
            Track ${_state.trackingOpen === s.orderId ? '▴' : '▾'}
          </button>
          ${s.awbCode ? `
            <button class="btn btn-sm btn-secondary adm-copy-awb-btn" data-awb="${_esc(s.awbCode)}">
              Copy AWB
            </button>` : `
            <button class="btn btn-sm btn-primary adm-create-ship-btn" data-orderid="${s.orderId}">
              Create Shipment
            </button>`}
        </td>
      </tr>`;

    if (_state.trackingOpen !== s.orderId) return mainRow;

    const trackingContent = _state.trackingLoading
      ? '<div class="adm-loading" style="padding:var(--sp-3)">Fetching tracking info…</div>'
      : _renderTrackingContent();

    const trackRow = `
      <tr class="adm-track-row">
        <td colspan="7">${trackingContent}</td>
      </tr>`;

    return mainRow + trackRow;
  }).join('');

  el.innerHTML = `
    <table class="adm-table adm-table-full">
      <thead>
        <tr>
          <th>Order ID</th>
          <th>AWB Code</th>
          <th>Courier</th>
          <th>Booked Date</th>
          <th>EDD</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>`;

  // Track buttons
  el.querySelectorAll('.adm-track-btn').forEach(btn => {
    btn.addEventListener('click', () => _toggleTracking(btn.dataset.orderid));
  });

  // Copy AWB buttons
  el.querySelectorAll('.adm-copy-awb-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      navigator.clipboard.writeText(btn.dataset.awb).then(() => {
        showToast('success', 'Copied', `AWB code ${btn.dataset.awb} copied to clipboard.`);
      }).catch(() => {
        showToast('error', 'Error', 'Failed to copy AWB code.');
      });
    });
  });

  // Create shipment buttons
  el.querySelectorAll('.adm-create-ship-btn').forEach(btn => {
    btn.addEventListener('click', () => _createShipment(btn.dataset.orderid, btn));
  });
}

function _renderTrackingContent() {
  if (!_state.trackingData) {
    return '<p class="adm-empty-msg" style="padding:var(--sp-3)">No tracking data available.</p>';
  }

  const td = _state.trackingData.data || _state.trackingData;
  const steps = td.trackingSteps || td.steps || td.activities || [];

  if (!steps.length) {
    return '<p class="adm-empty-msg" style="padding:var(--sp-3)">No tracking events yet.</p>';
  }

  return `
    <div style="padding:var(--sp-3)">
      <ul class="adm-track-list">
        ${steps.map(step => `
          <li class="adm-track-item">
            <span class="adm-track-date">${step.date ? formatDate(step.date) : (step.timestamp ? formatDate(step.timestamp) : '')}</span>
            <span class="adm-track-desc">${_esc(step.description || step.activity || step.message || '')}</span>
            ${step.location ? `<span class="adm-muted"> — ${_esc(step.location)}</span>` : ''}
          </li>`).join('')}
      </ul>
    </div>`;
}

async function _toggleTracking(orderId) {
  if (_state.trackingOpen === orderId) {
    // Collapse
    _state.trackingOpen = null;
    _state.trackingData = null;
    _renderList();
    return;
  }

  // Expand and fetch
  _state.trackingOpen = orderId;
  _state.trackingData = null;
  _state.trackingLoading = true;
  _renderList();

  try {
    const result = await adminTrackShipment(orderId);
    _state.trackingData = result;
  } catch (err) {
    _state.trackingData = { _error: err.message || 'Failed to fetch tracking info.' };
  } finally {
    _state.trackingLoading = false;
    _renderList();
  }
}

async function _createShipment(orderId, btn) {
  if (!confirm('Create a Shiprocket shipment for this order?')) return;
  btn.disabled = true;
  btn.textContent = 'Creating…';
  try {
    await adminCreateShipment(orderId);
    showToast('success', 'Shipment Created', `Shipment created for order ${orderId.slice(0, 8).toUpperCase()}.`);
    await _load();
  } catch (err) {
    showToast('error', 'Error', err.message || 'Failed to create shipment.');
    btn.disabled = false;
    btn.textContent = 'Create Shipment';
  }
}

function _renderPaging() {
  const el = document.getElementById('admShipPaging');
  if (!el) return;
  const totalPages = Math.ceil(_state.total / _state.size);
  if (totalPages <= 1) { el.classList.add('hidden'); return; }
  el.classList.remove('hidden');
  el.innerHTML = `
    <button class="btn btn-sm btn-secondary" id="admSPrev" ${_state.page <= 1 ? 'disabled' : ''}>← Prev</button>
    <span class="ord-page-info">Page ${_state.page} of ${totalPages}</span>
    <button class="btn btn-sm btn-secondary" id="admSNext" ${_state.page >= totalPages ? 'disabled' : ''}>Next →</button>`;
  document.getElementById('admSPrev')?.addEventListener('click', () => { _state.page--; _load(); });
  document.getElementById('admSNext')?.addEventListener('click', () => { _state.page++; _load(); });
}

function _shipBadgeClass(status) {
  const map = {
    NEW:                'pending',
    PICKUP_SCHEDULED:   'processing',
    PICKED_UP:          'processing',
    IN_TRANSIT:         'shipped',
    OUT_FOR_DELIVERY:   'shipped',
    DELIVERED:          'delivered',
    UNDELIVERED:        'cancelled',
  };
  return map[status] || 'pending';
}

function _markActiveNav(route) {
  document.querySelectorAll('.adm-nav-link').forEach(l => {
    l.classList.toggle('active', l.dataset.route === route);
  });
}

function _esc(s) {
  return s == null ? '' : String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
