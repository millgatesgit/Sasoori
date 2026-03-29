/**
 * SASOORI — Admin Products Page
 * Route: #/admin/products  (requires ADMIN role)
 */

import { getProducts, getCategories } from '../../api/productApi.js';
import { adminCreateProduct, adminUpdateProduct, adminDeleteProduct } from '../../api/adminApi.js';
import { formatPrice } from '../../utils/formatters.js';
import { showToast } from '../../components/toast.js';
import { _shell } from './dashboard.js';

let _state = {
  products: [], categories: [], total: 0, page: 1, size: 20,
  editing: null,   // product being edited (null = add new)
  showForm: false,
  loading: false,
};

const page = {
  meta: () => ({ title: 'Admin — Products', description: '' }),

  render(container) {
    container.innerHTML = _shell(`
      <div class="adm-page-header">
        <h1 class="adm-page-title">Products</h1>
        <button class="btn btn-primary btn-sm" id="admAddProdBtn">+ Add Product</button>
      </div>
      <div class="co-error hidden" id="admProdError"></div>
      <div id="admProdList"></div>
      <div id="admProdPaging" class="ord-paging hidden"></div>
      <div id="admProdPanel"></div>
    `);
    _markActiveNav('/admin/products');
  },

  async onMount() {
    document.getElementById('admAddProdBtn')?.addEventListener('click', () => {
      _state.editing = null;
      _state.showForm = !_state.showForm;
      _renderForm();
    });
    await Promise.all([_loadProducts(), _loadCategories()]);
  },

  onDestroy() {
    _state = { products: [], categories: [], total: 0, page: 1, size: 20, editing: null, showForm: false, loading: false };
  },
};

export default page;

async function _loadProducts() {
  _state.loading = true;
  _renderList();
  try {
    const data = await getProducts({ page: _state.page, size: _state.size });
    const d = data.data || data;
    _state.products = d.products || [];
    _state.total    = d.total    || 0;
  } catch (err) {
    _showError(err.message || 'Failed to load products.');
  } finally {
    _state.loading = false;
    _renderList();
    _renderPaging();
  }
}

async function _loadCategories() {
  try {
    const data = await getCategories();
    _state.categories = data.categories || data.data?.categories || data.data || [];
  } catch { /* non-fatal */ }
}

// ── Rendering ─────────────────────────────────────────────────────────────

function _renderList() {
  const el = document.getElementById('admProdList');
  if (!el) return;

  if (_state.loading) { el.innerHTML = '<div class="adm-loading">Loading…</div>'; return; }

  if (!_state.products.length) {
    el.innerHTML = '<p class="adm-empty-msg">No products found.</p>';
    return;
  }

  el.innerHTML = `
    <table class="adm-table adm-table-full">
      <thead>
        <tr>
          <th>Name</th><th>SKU</th><th>Category</th>
          <th>Price</th><th>Stock</th><th>Status</th><th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${_state.products.map(p => `
          <tr>
            <td class="adm-td-name">${_esc(p.name)}</td>
            <td class="adm-muted">${_esc(p.sku)}</td>
            <td>${_esc(p.categoryName || '')}</td>
            <td>${formatPrice(p.pricePaise)}</td>
            <td class="${p.stockQty < 10 ? 'adm-warn-text' : ''}">${p.stockQty}</td>
            <td><span class="adm-status-dot ${p.isActive ? 'adm-dot-active' : 'adm-dot-inactive'}">${p.isActive ? 'Active' : 'Inactive'}</span></td>
            <td class="adm-td-actions">
              <button class="btn btn-sm btn-ghost adm-edit-btn" data-id="${p.id}">Edit</button>
              <button class="btn btn-sm btn-ghost btn-danger adm-del-btn" data-id="${p.id}" data-name="${_esc(p.name)}">Delete</button>
            </td>
          </tr>`).join('')}
      </tbody>
    </table>`;

  el.querySelectorAll('.adm-edit-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const p = _state.products.find(x => x.id === btn.dataset.id);
      if (!p) return;
      _state.editing = p;
      _state.showForm = true;
      _renderForm();
    });
  });

  el.querySelectorAll('.adm-del-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      if (!confirm(`Delete "${btn.dataset.name}"? This cannot be undone.`)) return;
      btn.disabled = true;
      try {
        await adminDeleteProduct(btn.dataset.id);
        showToast('success', 'Deleted', 'Product deleted successfully.');
        await _loadProducts();
      } catch (err) {
        showToast('error', 'Error', err.message || 'Delete failed.');
        btn.disabled = false;
      }
    });
  });
}

function _renderForm() {
  const el = document.getElementById('admProdPanel');
  if (!el) return;
  if (!_state.showForm) { el.innerHTML = ''; return; }

  const p    = _state.editing;
  const cats = _state.categories;

  el.innerHTML = `
    <div class="adm-panel-overlay" id="admProdOverlay">
      <div class="adm-panel">
        <div class="adm-panel-header">
          <span class="adm-panel-title">${p ? '✏️ Edit Product' : '+ Add Product'}</span>
          <button class="adm-panel-close" id="admProdPanelClose">✕</button>
        </div>
        <div class="adm-panel-body">
          <form id="admProdFormEl" class="adm-form">
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Name*</label>
                <input class="form-input" name="name" value="${_esc(p?.name || '')}" required />
              </div>
              <div class="form-group">
                <label class="form-label">SKU*</label>
                <input class="form-input" name="sku" value="${_esc(p?.sku || '')}" required />
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Slug*</label>
                <input class="form-input" name="slug" value="${_esc(p?.slug || '')}" required />
              </div>
              <div class="form-group">
                <label class="form-label">Category*</label>
                <select class="form-input" name="categoryId" required>
                  <option value="">Select category</option>
                  ${cats.map(c => `<option value="${c.id}" ${p?.categoryId === c.id ? 'selected' : ''}>${_esc(c.name)}</option>`).join('')}
                </select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Price (paise)*</label>
                <input class="form-input" name="pricePaise" type="number" min="1" value="${p?.pricePaise || ''}" required />
              </div>
              <div class="form-group">
                <label class="form-label">MRP (paise)</label>
                <input class="form-input" name="mrpPaise" type="number" min="0" value="${p?.mrpPaise || 0}" />
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Stock Qty*</label>
                <input class="form-input" name="stockQty" type="number" min="0" value="${p?.stockQty ?? 0}" required />
              </div>
              <div class="form-group">
                <label class="form-label">Weight (g)</label>
                <input class="form-input" name="weightGrams" type="number" min="0" value="${p?.weightGrams || 0}" />
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">Description</label>
              <textarea class="form-input" name="description" rows="3">${_esc(p?.description || '')}</textarea>
            </div>
            <div class="form-row">
              <label class="form-checkbox">
                <input type="checkbox" name="isActive" ${p?.isActive !== false ? 'checked' : ''} /> Active
              </label>
              <label class="form-checkbox">
                <input type="checkbox" name="isFeatured" ${p?.isFeatured ? 'checked' : ''} /> Featured
              </label>
            </div>
            <div class="co-error hidden" id="admProdFormErr"></div>
          </form>
        </div>
        <div class="adm-panel-footer">
          <button type="button" class="btn btn-secondary" id="admProdPanelCancel">Cancel</button>
          <button type="button" class="btn btn-primary" id="admProdPanelSave">${p ? 'Save Changes' : 'Create Product'}</button>
        </div>
      </div>
    </div>`;

  const closePanel = () => { _state.showForm = false; _state.editing = null; _renderForm(); };

  document.getElementById('admProdPanelClose')?.addEventListener('click', closePanel);
  document.getElementById('admProdPanelCancel')?.addEventListener('click', closePanel);
  document.getElementById('admProdOverlay')?.addEventListener('click', e => {
    if (e.target.id === 'admProdOverlay') closePanel();
  });
  document.getElementById('admProdPanelSave')?.addEventListener('click', () => {
    document.getElementById('admProdFormEl')?.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
  });
  document.getElementById('admProdFormEl')?.addEventListener('submit', _onSubmitProduct);
}

async function _onSubmitProduct(e) {
  e.preventDefault();
  const form = e.target;
  const errEl = document.getElementById('admProdFormErr');
  const btn = document.getElementById('admProdPanelSave');

  const data = {
    name:        form.elements['name'].value.trim(),
    sku:         form.sku.value.trim(),
    slug:        form.slug.value.trim(),
    categoryId:  parseInt(form.categoryId.value),
    pricePaise:  parseInt(form.pricePaise.value),
    mrpPaise:    parseInt(form.mrpPaise.value) || 0,
    stockQty:    parseInt(form.stockQty.value),
    weightGrams: parseInt(form.weightGrams.value) || 0,
    description: form.description.value.trim(),
    isActive:    form.isActive.checked,
    isFeatured:  form.isFeatured.checked,
  };

  if (btn) btn.disabled = true;
  errEl?.classList.add('hidden');

  try {
    if (_state.editing) {
      await adminUpdateProduct(_state.editing.id, data);
      showToast('success', 'Saved', 'Product updated.');
    } else {
      await adminCreateProduct(data);
      showToast('success', 'Created', 'Product created.');
    }
    _state.showForm = false;
    _state.editing  = null;
    _renderForm();
    await _loadProducts();
  } catch (err) {
    if (errEl) { errEl.textContent = err.message || 'Save failed.'; errEl.classList.remove('hidden'); }
    if (btn) btn.disabled = false;
  }
}

function _renderPaging() {
  const el = document.getElementById('admProdPaging');
  if (!el) return;
  const totalPages = Math.ceil(_state.total / _state.size);
  if (totalPages <= 1) { el.classList.add('hidden'); return; }
  el.classList.remove('hidden');
  el.innerHTML = `
    <button class="btn btn-sm btn-secondary" id="admPPrev" ${_state.page <= 1 ? 'disabled' : ''}>← Prev</button>
    <span class="ord-page-info">Page ${_state.page} of ${totalPages}</span>
    <button class="btn btn-sm btn-secondary" id="admPNext" ${_state.page >= totalPages ? 'disabled' : ''}>Next →</button>`;
  document.getElementById('admPPrev')?.addEventListener('click', () => { _state.page--; _loadProducts(); });
  document.getElementById('admPNext')?.addEventListener('click', () => { _state.page++; _loadProducts(); });
}

function _showError(msg) {
  const el = document.getElementById('admProdError');
  if (el) { el.textContent = msg; el.classList.remove('hidden'); }
}

function _esc(s) {
  return s == null ? '' : String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function _markActiveNav(route) {
  document.querySelectorAll('.adm-nav-link').forEach(l => {
    l.classList.toggle('active', l.dataset.route === route);
  });
}
