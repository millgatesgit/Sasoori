/**
 * SASOORI — Products Listing Page
 * Route: #/products
 * Supports: ?category=oils|masala|flours|health, ?search=, ?sort=, ?page=
 */

import { productCardHTML, attachCardEvents, skeletonCards } from '../components/productCard.js';
import { getProducts, getCategories } from '../api/productApi.js';

const ITEMS_PER_PAGE = 12;

// Page-level state
let _currentParams = {};
let _filtersOpen   = false;
let _apiCategories = [];   // loaded once from API

const page = {
  meta: () => {
    const cat = _currentParams.category;
    const found = _apiCategories.find(c => c.slug === cat);
    return {
      title: found ? found.name : 'All Products',
      description: found ? found.description : 'Browse Sasoori\'s complete range of pure, natural products.',
    };
  },

  render(container, _routeArg) {
    _currentParams = _parseParams();
    container.innerHTML = _buildTemplate();
  },

  async onMount() {
    // Load categories once for filter pills
    if (!_apiCategories.length) {
      try {
        const cats = await getCategories();
        _apiCategories = Array.isArray(cats) ? cats : (cats?.categories || []);
        // Re-render category pills with real data
        const pills = document.getElementById('categoryPills');
        if (pills) pills.innerHTML = _buildCategoryPills();
      } catch { /* use empty list */ }
    }
    _bindFilters();
    _bindSearch();
    _renderProducts();
  },

  onDestroy() {
    _filtersOpen = false;
  },
};

// ── Helpers ─────────────────────────────────────────────────────

function _parseParams() {
  const hash   = window.location.hash || '';
  const qIndex = hash.indexOf('?');
  if (qIndex === -1) return {};
  const qs = new URLSearchParams(hash.slice(qIndex + 1));
  return {
    category: qs.get('category') || '',
    search:   qs.get('search')   || '',
    sort:     qs.get('sort')     || 'popular',
    page:     parseInt(qs.get('page') || '1', 10),
    tag:      qs.get('tag')      || '',
  };
}

async function _renderProducts() {
  const grid  = document.getElementById('productsGrid');
  const count = document.getElementById('productsCount');
  const pagination = document.getElementById('productsPagination');
  if (!grid) return;

  grid.innerHTML = skeletonCards(ITEMS_PER_PAGE);

  try {
    const params = {
      page:     _currentParams.page     || 1,
      size:     ITEMS_PER_PAGE,
      sort:     _currentParams.sort     || undefined,
      search:   _currentParams.search   || undefined,
      category: _currentParams.category || undefined,
      tag:      _currentParams.tag      || undefined,
    };
    const result = await getProducts(params);
    const products   = result.products   || [];
    const total      = result.total      || 0;
    const totalPages = result.totalPages || 1;

    if (count) count.textContent = `${total} product${total !== 1 ? 's' : ''}`;

    if (!products.length) {
      grid.innerHTML = `
        <div style="grid-column:1/-1;text-align:center;padding:var(--sp-16) var(--sp-4)">
          <div style="font-size:3rem;margin-bottom:var(--sp-4)">🔍</div>
          <h3 style="margin-bottom:var(--sp-2)">No products found</h3>
          <p style="color:var(--clr-text-m)">Try adjusting your filters or <a href="#/products" style="color:var(--clr-primary)">view all products</a>.</p>
        </div>`;
      if (pagination) pagination.innerHTML = '';
      return;
    }

    grid.innerHTML = products.map(productCardHTML).join('');
    attachCardEvents(grid);

    if (pagination) pagination.innerHTML = _buildPagination(totalPages, _currentParams.page || 1);
  } catch {
    grid.innerHTML = `
      <div style="grid-column:1/-1;text-align:center;padding:var(--sp-16) var(--sp-4)">
        <h3>Could not load products</h3>
        <p style="color:var(--clr-text-m)">Please try refreshing the page.</p>
      </div>`;
  }
}

function _buildPagination(totalPages, currentPage) {
  if (totalPages <= 1) return '';
  const params = { ..._currentParams };
  const pages = [];

  for (let i = 1; i <= totalPages; i++) {
    const p = { ...params, page: i };
    const qs = new URLSearchParams(Object.fromEntries(Object.entries(p).filter(([,v]) => v)));
    const href = `#/products?${qs}`;
    pages.push(`<a href="${href}" class="page-btn${i === currentPage ? ' active' : ''}" aria-label="Page ${i}" aria-current="${i === currentPage ? 'page' : 'false'}">${i}</a>`);
  }

  const prev = currentPage > 1        ? `<a href="${_pageHref(currentPage - 1)}" class="page-btn" aria-label="Previous">‹</a>` : `<span class="page-btn disabled">‹</span>`;
  const next = currentPage < totalPages ? `<a href="${_pageHref(currentPage + 1)}" class="page-btn" aria-label="Next">›</a>` : `<span class="page-btn disabled">›</span>`;

  return `<nav class="pagination" aria-label="Page navigation">${prev}${pages.join('')}${next}</nav>`;
}

function _pageHref(p) {
  const params = { ..._currentParams, page: p };
  const qs = new URLSearchParams(Object.fromEntries(Object.entries(params).filter(([,v]) => v)));
  return `#/products?${qs}`;
}

function _bindFilters() {
  // Sort selects (toolbar + sidebar)
  ['sortSelect', 'sortSelectSidebar'].forEach(id => {
    const sortEl = document.getElementById(id);
    if (!sortEl) return;
    sortEl.value = _currentParams.sort || 'popular';
    sortEl.addEventListener('change', () => _updateParam('sort', sortEl.value));
  });

  // Category pills
  document.querySelectorAll('[data-category-filter]').forEach(btn => {
    btn.addEventListener('click', () => {
      const val = btn.dataset.categoryFilter;
      _updateParam('category', val);
    });
  });

  // Mobile filter toggle
  const filterBtn = document.getElementById('filterToggleBtn');
  const filterPanel = document.getElementById('filterPanel');
  if (filterBtn && filterPanel) {
    filterBtn.addEventListener('click', () => {
      _filtersOpen = !_filtersOpen;
      filterPanel.classList.toggle('open', _filtersOpen);
      filterBtn.setAttribute('aria-expanded', _filtersOpen);
    });
    // Close on backdrop click
    filterPanel.addEventListener('click', (e) => {
      if (e.target === filterPanel) {
        _filtersOpen = false;
        filterPanel.classList.remove('open');
      }
    });
  }
}

function _bindSearch() {
  const searchEl = document.getElementById('productsSearchInput');
  if (!searchEl) return;
  searchEl.value = _currentParams.search || '';
  let timer;
  searchEl.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => _updateParam('search', searchEl.value.trim()), 300);
  });
}

function _updateParam(key, value) {
  _currentParams[key] = value;
  _currentParams.page = 1; // reset to page 1 on filter change

  const qs = new URLSearchParams(Object.fromEntries(Object.entries(_currentParams).filter(([,v]) => v)));
  window.location.hash = `/products?${qs}`;

  _renderProducts();

  // Update active states on category pills
  document.querySelectorAll('[data-category-filter]').forEach(btn => {
    const active = btn.dataset.categoryFilter === (_currentParams.category || '');
    btn.classList.toggle('active', active);
    btn.setAttribute('aria-pressed', active);
  });
}

// ── Template ─────────────────────────────────────────────────────

function _buildCategoryPills() {
  const activeCat = _currentParams.category || '';
  return [
    { slug: '', label: 'All Products' },
    ..._apiCategories.map(c => ({ slug: c.slug, label: c.name })),
  ].map(c => `
    <button
      class="filter-pill${c.slug === activeCat ? ' active' : ''}"
      data-category-filter="${c.slug}"
      aria-pressed="${c.slug === activeCat}"
    >${c.label}</button>
  `).join('');
}

function _buildTemplate() {
  const activeCat  = _currentParams.category || '';
  const activeSort = _currentParams.sort || 'popular';
  const activeSearch = _currentParams.search || '';

  const catLabel = _apiCategories.find(c => c.slug === activeCat)?.name || 'All Products';

  return `
  <!-- Breadcrumb -->
  <div class="container" style="padding-top:var(--sp-4);padding-bottom:var(--sp-2)">
    <nav class="breadcrumb" aria-label="Breadcrumb">
      <a href="#/">Home</a>
      <span aria-hidden="true">›</span>
      <span aria-current="page">${catLabel}</span>
    </nav>
  </div>

  <!-- Page header -->
  <div class="container" style="padding-bottom:var(--sp-6)">
    <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-4)">
      <div>
        <h1 style="font-size:var(--ts-2xl);margin-bottom:var(--sp-1)">${catLabel}</h1>
        <p style="color:var(--clr-text-m);font-size:var(--ts-sm)" id="productsCount"></p>
      </div>

      <!-- Mobile filter button -->
      <button class="btn btn-secondary btn-sm filter-toggle-btn" id="filterToggleBtn" aria-expanded="false" style="display:flex;align-items:center;gap:var(--sp-2)">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><line x1="4" y1="6" x2="20" y2="6"/><line x1="8" y1="12" x2="16" y2="12"/><line x1="11" y1="18" x2="13" y2="18"/></svg>
        Filters
      </button>
    </div>
  </div>

  <div class="container" style="padding-bottom:var(--sp-12)">
    <div class="products-layout">

      <!-- ── SIDEBAR / FILTER PANEL ── -->
      <aside class="filter-sidebar" id="filterPanel" role="complementary" aria-label="Product filters">
        <div class="filter-sidebar-inner">

          <!-- Search within products -->
          <div class="filter-section">
            <h3 class="filter-heading">Search</h3>
            <div style="position:relative">
              <input
                type="search"
                id="productsSearchInput"
                class="form-input"
                placeholder="Search products…"
                value="${activeSearch}"
                style="padding-left:var(--sp-8)"
              />
              <svg style="position:absolute;left:var(--sp-3);top:50%;transform:translateY(-50%);color:var(--clr-text-m)" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
            </div>
          </div>

          <!-- Category filter -->
          <div class="filter-section">
            <h3 class="filter-heading">Category</h3>
            <div id="categoryPills" style="display:flex;flex-direction:column;gap:var(--sp-2)">
              ${_buildCategoryPills()}
            </div>
          </div>

          <!-- Sort (duplicated in sidebar for mobile) -->
          <div class="filter-section">
            <h3 class="filter-heading">Sort By</h3>
            <select class="form-select" id="sortSelectSidebar">
              <option value="popular"    ${activeSort==='popular'    ? 'selected' : ''}>Most Popular</option>
              <option value="newest"     ${activeSort==='newest'     ? 'selected' : ''}>Newest First</option>
              <option value="price_asc"  ${activeSort==='price_asc'  ? 'selected' : ''}>Price: Low to High</option>
              <option value="price_desc" ${activeSort==='price_desc' ? 'selected' : ''}>Price: High to Low</option>
            </select>
          </div>

        </div>
      </aside>

      <!-- ── MAIN CONTENT ── -->
      <div class="products-main">

        <!-- Toolbar (desktop sort + active filters) -->
        <div class="products-toolbar">
          <div style="display:flex;align-items:center;gap:var(--sp-3);flex-wrap:wrap">
            ${activeCat ? `
            <span class="active-filter-chip">
              ${catLabel}
              <a href="#/products" aria-label="Remove category filter" style="color:inherit;text-decoration:none">✕</a>
            </span>` : ''}
            ${activeSearch ? `
            <span class="active-filter-chip">
              "${activeSearch}"
              <a href="#/products${activeCat ? '?category=' + activeCat : ''}" aria-label="Clear search" style="color:inherit;text-decoration:none">✕</a>
            </span>` : ''}
          </div>
          <div style="display:flex;align-items:center;gap:var(--sp-2)">
            <label for="sortSelect" style="font-size:var(--ts-sm);color:var(--clr-text-m);white-space:nowrap">Sort:</label>
            <select class="form-select form-select-sm" id="sortSelect" style="width:auto">
              <option value="popular"    ${activeSort==='popular'    ? 'selected' : ''}>Most Popular</option>
              <option value="newest"     ${activeSort==='newest'     ? 'selected' : ''}>Newest First</option>
              <option value="price_asc"  ${activeSort==='price_asc'  ? 'selected' : ''}>Price ↑</option>
              <option value="price_desc" ${activeSort==='price_desc' ? 'selected' : ''}>Price ↓</option>
            </select>
          </div>
        </div>

        <!-- Products grid -->
        <div class="product-grid" id="productsGrid">
          ${skeletonCards(ITEMS_PER_PAGE)}
        </div>

        <!-- Pagination -->
        <div id="productsPagination" style="margin-top:var(--sp-10)"></div>

      </div>
    </div>
  </div>
  `;
}

export default page;
