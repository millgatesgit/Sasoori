/**
 * SASOORI — Admin Users Page
 * Route: #/admin/users  (requires ADMIN role)
 */

import { adminGetUsers, adminSetUserActive, adminSetUserRole, adminGetOrders } from '../../api/adminApi.js';
import { formatDate, formatPrice, formatOrderStatus, orderStatusBadgeClass } from '../../utils/formatters.js';
import { showToast } from '../../components/toast.js';
import { _shell } from './dashboard.js';

let _state = {
  users: [], total: 0, page: 1, size: 20, loading: false,
  detailUserId: null, detailUser: null, detailOrders: [], detailLoading: false,
};

const page = {
  meta: () => ({ title: 'Admin — Users', description: '' }),

  render(container) {
    container.innerHTML = _shell(`
      <h1 class="adm-page-title">Users</h1>
      <div class="co-error hidden" id="admUsrError"></div>
      <div id="admUsrList"></div>
      <div id="admUsrPaging" class="ord-paging hidden"></div>
      <div id="admUsrPanel"></div>
    `);
    _markActiveNav('/admin/users');
  },

  async onMount() {
    await _load();
  },

  onDestroy() {
    _state = { users: [], total: 0, page: 1, size: 20, loading: false, detailUserId: null, detailUser: null, detailOrders: [], detailLoading: false };
  },
};

export default page;

async function _load() {
  _state.loading = true;
  _renderList();
  try {
    const data = await adminGetUsers({ page: _state.page, size: _state.size });
    const d = data.data || data;
    _state.users = d.users || [];
    _state.total = d.total || 0;
  } catch (err) {
    const el = document.getElementById('admUsrError');
    if (el) { el.textContent = err.message || 'Failed to load users.'; el.classList.remove('hidden'); }
  } finally {
    _state.loading = false;
    _renderList();
    _renderPaging();
  }
}

function _renderList() {
  const el = document.getElementById('admUsrList');
  if (!el) return;

  if (_state.loading) { el.innerHTML = '<div class="adm-loading">Loading…</div>'; return; }

  if (!_state.users.length) {
    el.innerHTML = '<p class="adm-empty-msg">No users found.</p>';
    return;
  }

  el.innerHTML = `
    <table class="adm-table adm-table-full">
      <thead>
        <tr><th>Name / Email</th><th>Phone</th><th>Role</th><th>Active</th><th>Joined</th><th>Actions</th></tr>
      </thead>
      <tbody>
        ${_state.users.map(u => `
          <tr>
            <td>
              <div class="adm-user-name">${_esc(u.name || '—')}</div>
              <div class="adm-muted adm-user-email">${_esc(u.email || '')}</div>
            </td>
            <td class="adm-muted">${_esc(u.phone || '—')}</td>
            <td>
              <select class="form-input adm-role-select" data-id="${u.id}" data-current="${u.role}">
                <option value="CUSTOMER" ${u.role === 'CUSTOMER' ? 'selected' : ''}>Customer</option>
                <option value="ADMIN"    ${u.role === 'ADMIN'    ? 'selected' : ''}>Admin</option>
              </select>
            </td>
            <td>
              <label class="adm-toggle">
                <input type="checkbox" class="adm-active-chk" data-id="${u.id}" ${u.isActive ? 'checked' : ''} />
                <span class="adm-toggle-label">${u.isActive ? 'Yes' : 'No'}</span>
              </label>
            </td>
            <td class="adm-muted">${formatDate(u.createdAt)}</td>
            <td>
              <button class="btn btn-sm btn-ghost adm-view-user-btn" data-id="${u.id}" data-name="${_esc(u.name || u.email || 'User')}">View</button>
            </td>
          </tr>`).join('')}
      </tbody>
    </table>`;

  // Role change
  el.querySelectorAll('.adm-role-select').forEach(sel => {
    sel.addEventListener('change', async () => {
      const id      = sel.dataset.id;
      const current = sel.dataset.current;
      const newRole = sel.value;
      if (newRole === current) return;
      if (!confirm(`Change role to ${newRole}?`)) { sel.value = current; return; }
      sel.disabled = true;
      try {
        await adminSetUserRole(id, newRole);
        showToast('success', 'Updated', `Role changed to ${newRole}.`);
        sel.dataset.current = newRole;
      } catch (err) {
        showToast('error', 'Error', err.message || 'Role update failed.');
        sel.value = current;
      } finally {
        sel.disabled = false;
      }
    });
  });

  // Active toggle
  el.querySelectorAll('.adm-active-chk').forEach(chk => {
    chk.addEventListener('change', async () => {
      const id     = chk.dataset.id;
      const active = chk.checked;
      const label  = chk.nextElementSibling;
      chk.disabled = true;
      try {
        await adminSetUserActive(id, active);
        showToast('success', 'Updated', `User ${active ? 'activated' : 'deactivated'}.`);
        if (label) label.textContent = active ? 'Yes' : 'No';
      } catch (err) {
        showToast('error', 'Error', err.message || 'Update failed.');
        chk.checked = !active;
      } finally {
        chk.disabled = false;
      }
    });
  });

  // View user detail
  el.querySelectorAll('.adm-view-user-btn').forEach(btn => {
    btn.addEventListener('click', () => _openUserDetail(btn.dataset.id, btn.dataset.name));
  });
}

async function _openUserDetail(userId, userName) {
  _state.detailUserId = userId;
  _state.detailUser = _state.users.find(u => u.id === userId) || { name: userName };
  _state.detailOrders = [];
  _state.detailLoading = true;
  _renderUserPanel();
  try {
    const data = await adminGetOrders({ page: 1, size: 10 });
    // Filter orders by userId — backend returns all; filter client-side
    // (A proper endpoint would filter by userId, but we use what exists)
    const all = (data.data || data).orders || [];
    _state.detailOrders = all.filter(o => o.userId === userId || o.user_id === userId);
    _state.detailLoading = false;
    _renderUserPanel();
  } catch {
    _state.detailLoading = false;
    _renderUserPanel();
  }
}

function _closeUserDetail() {
  _state.detailUserId = null;
  _state.detailUser = null;
  _state.detailOrders = [];
  _renderUserPanel();
}

function _renderUserPanel() {
  const el = document.getElementById('admUsrPanel');
  if (!el) return;
  if (!_state.detailUserId) { el.innerHTML = ''; return; }

  const u = _state.detailUser || {};
  const orders = _state.detailOrders;
  const totalSpend = orders.reduce((s, o) => s + (o.totalPaise || o.total_paise || 0), 0);

  el.innerHTML = `
    <div class="adm-panel-overlay" id="admUsrOverlay">
      <div class="adm-panel">
        <div class="adm-panel-header">
          <span class="adm-panel-title">👤 ${_esc(u.name || u.email || 'User')}</span>
          <button class="adm-panel-close" id="admUsrPanelClose">✕</button>
        </div>
        <div class="adm-panel-body">
          ${_state.detailLoading ? '<div class="adm-loading">Loading…</div>' : `
            <!-- User info -->
            <div style="background:var(--clr-bg);border-radius:var(--r-md);padding:var(--sp-4);margin-bottom:var(--sp-5)">
              ${u.email    ? `<div style="font-size:var(--ts-sm)"><strong>Email:</strong> ${_esc(u.email)}</div>` : ''}
              ${u.phone    ? `<div style="font-size:var(--ts-sm)"><strong>Phone:</strong> ${_esc(u.phone)}</div>` : ''}
              ${u.role     ? `<div style="font-size:var(--ts-sm)"><strong>Role:</strong> ${_esc(u.role)}</div>` : ''}
              ${u.createdAt? `<div style="font-size:var(--ts-sm)"><strong>Joined:</strong> ${formatDate(u.createdAt)}</div>` : ''}
            </div>

            <!-- Stats -->
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--sp-3);margin-bottom:var(--sp-5)">
              <div class="adm-stat-card" style="cursor:default">
                <div class="adm-stat-label">Total Spend</div>
                <div class="adm-stat-value" style="font-size:var(--ts-lg)">${formatPrice(totalSpend)}</div>
              </div>
              <div class="adm-stat-card" style="cursor:default">
                <div class="adm-stat-label">Orders</div>
                <div class="adm-stat-value" style="font-size:var(--ts-lg)">${orders.length}</div>
              </div>
            </div>

            <!-- Order history -->
            <h4 style="font-size:var(--ts-sm);font-weight:600;margin-bottom:var(--sp-3);color:var(--clr-text-m);text-transform:uppercase;letter-spacing:.05em">Order History</h4>
            ${orders.length === 0
              ? `<p class="adm-empty-msg">No recent orders found.</p>`
              : `<div style="border:1px solid var(--clr-border);border-radius:var(--r-md);overflow:hidden">
                  ${orders.map(o => `
                    <div style="display:flex;justify-content:space-between;align-items:center;padding:var(--sp-3) var(--sp-4);border-bottom:1px solid var(--clr-divider)">
                      <div>
                        <div style="font-size:var(--ts-sm);font-weight:500">#${(o.id||'').slice(0,8).toUpperCase()}</div>
                        <div style="font-size:var(--ts-xs);color:var(--clr-text-m)">${formatDate(o.createdAt || o.created_at)}</div>
                      </div>
                      <div style="display:flex;align-items:center;gap:var(--sp-3)">
                        <span class="ord-badge ${orderStatusBadgeClass(o.status)}">${formatOrderStatus(o.status)}</span>
                        <span style="font-weight:600;font-size:var(--ts-sm)">${formatPrice(o.totalPaise || o.total_paise || 0)}</span>
                      </div>
                    </div>`).join('')}
                </div>`
            }
          `}
        </div>
        <div class="adm-panel-footer">
          <button class="btn btn-secondary" id="admUsrPanelClose2">Close</button>
        </div>
      </div>
    </div>`;

  document.getElementById('admUsrPanelClose')?.addEventListener('click', _closeUserDetail);
  document.getElementById('admUsrPanelClose2')?.addEventListener('click', _closeUserDetail);
  document.getElementById('admUsrOverlay')?.addEventListener('click', e => {
    if (e.target.id === 'admUsrOverlay') _closeUserDetail();
  });
}

function _renderPaging() {
  const el = document.getElementById('admUsrPaging');
  if (!el) return;
  const totalPages = Math.ceil(_state.total / _state.size);
  if (totalPages <= 1) { el.classList.add('hidden'); return; }
  el.classList.remove('hidden');
  el.innerHTML = `
    <button class="btn btn-sm btn-secondary" id="admUPrev" ${_state.page <= 1 ? 'disabled' : ''}>← Prev</button>
    <span class="ord-page-info">Page ${_state.page} of ${totalPages}</span>
    <button class="btn btn-sm btn-secondary" id="admUNext" ${_state.page >= totalPages ? 'disabled' : ''}>Next →</button>`;
  document.getElementById('admUPrev')?.addEventListener('click', () => { _state.page--; _load(); });
  document.getElementById('admUNext')?.addEventListener('click', () => { _state.page++; _load(); });
}

function _esc(s) {
  return s == null ? '' : String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function _markActiveNav(route) {
  document.querySelectorAll('.adm-nav-link').forEach(l => {
    l.classList.toggle('active', l.dataset.route === route);
  });
}
