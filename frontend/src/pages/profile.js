/**
 * SASOORI — My Profile Page
 * Route: #/profile  (requires auth)
 */

import { getProfile, updateProfile } from '../api/userApi.js';
import store from '../store.js';
import { showToast } from '../components/toast.js';

const page = {
  meta: () => ({ title: 'My Profile', description: '' }),

  render(container) {
    container.innerHTML = `
      <div class="profile-wrap">
        <h1 class="profile-heading">My Profile</h1>
        <div id="profileContent"><div class="profile-loading">Loading…</div></div>
      </div>`;
  },

  async onMount() {
    try {
      const user = await getProfile();
      _render(user);
    } catch (err) {
      const el = document.getElementById('profileContent');
      if (el) el.innerHTML = `<div class="co-error">${err.message || 'Failed to load profile.'}</div>`;
    }
  },

  onDestroy() {},
};

export default page;

// ── Render ────────────────────────────────────────────────────────────────────

function _render(user) {
  const el = document.getElementById('profileContent');
  if (!el) return;

  const isAdmin = user.role === 'ADMIN';

  el.innerHTML = `
    <!-- Avatar card -->
    <div class="profile-card profile-avatar-card">
      <div class="profile-avatar-wrap">
        ${_avatarHtml(user)}
      </div>
      <div class="profile-id-block">
        <div class="profile-name">${_esc(user.name || 'No name set')}</div>
        <span class="profile-role-badge ${isAdmin ? 'profile-role-admin' : ''}">${isAdmin ? 'Admin' : 'Customer'}</span>
      </div>
    </div>

    <!-- Details (view mode) -->
    <div class="profile-card" id="profileViewCard">
      ${_row('Full Name',     user.name  || '—')}
      ${_row('Email',         user.email || '—')}
      ${_row('Phone Number',  user.phone || '—')}
      <div class="profile-actions">
        <button class="btn btn-primary" id="editProfileBtn">Edit Profile</button>
      </div>
    </div>

    <!-- Edit form (hidden by default) -->
    <div class="profile-card hidden" id="profileEditCard">
      <div class="form-group">
        <label class="form-label">Full Name *</label>
        <input class="form-input" id="editName" type="text"
               value="${_esc(user.name || '')}" placeholder="Your full name" maxlength="100" />
      </div>
      <div class="form-group">
        <label class="form-label">Phone Number</label>
        <input class="form-input" id="editPhone" type="tel"
               value="${_esc(user.phone || '')}" placeholder="+91XXXXXXXXXX" />
        <p class="profile-hint">Indian mobile number — leave blank to remove.</p>
      </div>
      <div class="form-group">
        <label class="form-label">Email</label>
        <input class="form-input" type="email" value="${_esc(user.email || '')}"
               disabled style="opacity:.55;cursor:not-allowed" />
        <p class="profile-hint">Email is linked to your login provider and cannot be changed here.</p>
      </div>
      <p class="form-error hidden" id="profileEditErr"></p>
      <div class="profile-actions">
        <button class="btn btn-primary" id="saveProfileBtn">Save Changes</button>
        <button class="btn btn-secondary" id="cancelEditBtn">Cancel</button>
      </div>
    </div>`;

  _bindEvents(user);
}

function _bindEvents(user) {
  document.getElementById('editProfileBtn')?.addEventListener('click', () => {
    document.getElementById('profileViewCard').classList.add('hidden');
    document.getElementById('profileEditCard').classList.remove('hidden');
  });

  document.getElementById('cancelEditBtn')?.addEventListener('click', () => {
    document.getElementById('profileEditCard').classList.add('hidden');
    document.getElementById('profileViewCard').classList.remove('hidden');
    document.getElementById('profileEditErr').classList.add('hidden');
  });

  document.getElementById('saveProfileBtn')?.addEventListener('click', async () => {
    const name  = document.getElementById('editName')?.value.trim();
    const phone = document.getElementById('editPhone')?.value.trim();
    const errEl = document.getElementById('profileEditErr');
    const btn   = document.getElementById('saveProfileBtn');
    errEl.classList.add('hidden');

    if (!name) {
      errEl.textContent = 'Name is required.';
      errEl.classList.remove('hidden');
      return;
    }
    if (phone && !/^\+91[6-9]\d{9}$/.test(phone)) {
      errEl.textContent = 'Enter a valid Indian mobile number (+91XXXXXXXXXX) or leave blank.';
      errEl.classList.remove('hidden');
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Saving…';
    try {
      const updated = await updateProfile({ name, phone: phone || null });
      // Sync store so header avatar name is current
      store.setState({ user: { ...store.getState().user, name: updated.name, phone: updated.phone } });
      showToast('success', 'Saved', 'Your profile has been updated.');
      _render(updated);
    } catch (err) {
      errEl.textContent = err.message || 'Update failed. Please try again.';
      errEl.classList.remove('hidden');
      btn.disabled = false;
      btn.textContent = 'Save Changes';
    }
  });
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function _avatarHtml(user) {
  if (user.picture) {
    return `<img src="${_esc(user.picture)}" alt="${_esc(user.name || 'Profile')}" class="profile-avatar-img" />`;
  }
  const initials = (user.name || 'U')
    .split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
  return `<div class="profile-avatar-initials">${initials}</div>`;
}

function _row(label, value) {
  return `
    <div class="profile-row">
      <span class="profile-row-label">${label}</span>
      <span class="profile-row-value">${_esc(value)}</span>
    </div>`;
}

function _esc(s) {
  return s == null ? '' : String(s)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
