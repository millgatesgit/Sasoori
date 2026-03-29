/**
 * SASOORI — Product Detail Page
 * Route: #/product/:slug
 */

import { getProductBySlug, getProducts } from '../api/productApi.js';
import { addToCart } from '../api/cartApi.js';
import { formatPrice, discountPercent } from '../utils/formatters.js';
import { showToast } from '../components/toast.js';
import { productCardHTML, attachCardEvents } from '../components/productCard.js';
import store from '../store.js';

let _slug       = '';
let _product    = null;
let _qty        = 1;

const page = {
  meta: () => ({
    title: _product ? `${_product.name}${_product.weightGrams ? ' ' + _product.weightGrams + 'g' : ''}` : 'Product',
    description: _product?.description || 'Pure natural product by Sasoori.',
  }),

  render(container, routeArg) {
    const params = routeArg?.params || routeArg || {};
    _slug    = params?.slug || '';
    _product = null;
    _qty     = 1;

    container.innerHTML = _buildSkeleton();
  },

  async onMount() {
    if (!_slug) return;

    try {
      const data = await getProductBySlug(_slug);
      _product = data?.product || data;

      if (!_product?.id) {
        const main = document.getElementById('productDetailMain');
        if (main) main.innerHTML = `
          <div class="container" style="text-align:center;padding:var(--sp-20) var(--sp-4)">
            <div style="font-size:3rem;margin-bottom:var(--sp-4)">😔</div>
            <h2>Product Not Found</h2>
            <p style="color:var(--clr-text-m);margin:var(--sp-4) 0">The product you're looking for doesn't exist or may have been removed.</p>
            <a href="#/products" class="btn btn-primary">Browse All Products</a>
          </div>`;
        return;
      }

      const main = document.getElementById('productDetailMain');
      if (main) main.innerHTML = _buildTemplate(_product);

      _bindGallery();
      _bindQty();
      _bindAddToCart();
      _bindBuyNow();
      _bindTabs();
      await _renderRelated();

    } catch (err) {
      const main = document.getElementById('productDetailMain');
      if (main) main.innerHTML = `
        <div class="container" style="text-align:center;padding:var(--sp-20) var(--sp-4)">
          <h3>Could not load product</h3>
          <p style="color:var(--clr-text-m)">Please try refreshing the page.</p>
        </div>`;
    }
  },

  onDestroy() {
    _product  = null;
    _slug     = '';
    _qty      = 1;
  },
};

// ── Bindings ─────────────────────────────────────────────────────

function _bindGallery() {
  const thumbs = document.querySelectorAll('.gallery-thumb');
  const main   = document.getElementById('mainProductImg');
  thumbs.forEach((t, i) => {
    t.addEventListener('click', () => {
      if (main) {
        main.src = t.dataset.src || t.querySelector('img')?.src || '';
        main.alt = t.querySelector('img')?.alt || '';
      }
      thumbs.forEach((th, ti) => th.classList.toggle('active', ti === i));
    });
  });
}

function _bindQty() {
  const minus = document.getElementById('qtyMinus');
  const plus  = document.getElementById('qtyPlus');
  const input = document.getElementById('qtyInput');

  minus?.addEventListener('click', () => {
    if (_qty > 1) { _qty--; if (input) input.value = _qty; }
    if (minus) minus.disabled = _qty <= 1;
  });
  plus?.addEventListener('click', () => {
    if (_qty < 10) { _qty++; if (input) input.value = _qty; }
    if (plus) plus.disabled = _qty >= 10;
  });
  input?.addEventListener('change', () => {
    const v = parseInt(input.value, 10);
    _qty = Math.max(1, Math.min(10, isNaN(v) ? 1 : v));
    input.value = _qty;
  });
}

function _bindAddToCart() {
  const btn = document.getElementById('addToCartBtn');
  if (!btn) return;

  btn.addEventListener('click', async () => {
    if (!store.isLoggedIn()) {
      showToast('info', 'Sign in required', 'Please sign in to add items to your cart.');
      window.location.hash = '/login';
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Adding…';

    try {
      await addToCart(_product.id, _qty);
      btn.textContent = '✓ Added to Cart';
      btn.style.background = 'var(--clr-success)';
      const weightLabel = _product.weightGrams ? ` (${_product.weightGrams}g × ${_qty})` : ` × ${_qty}`;
      showToast('success', 'Added to cart', `${_product.name}${weightLabel} added.`);
      setTimeout(() => {
        btn.disabled = false;
        btn.textContent = 'Add to Cart';
        btn.style.background = '';
      }, 2000);
    } catch (err) {
      btn.disabled = false;
      btn.textContent = 'Add to Cart';
      showToast('error', 'Failed', err.message || 'Could not add to cart.');
    }
  });
}

function _bindBuyNow() {
  const btn = document.getElementById('buyNowBtn');
  if (!btn) return;

  btn.addEventListener('click', async () => {
    if (!store.isLoggedIn()) {
      showToast('info', 'Sign in required', 'Please sign in to purchase.');
      window.location.hash = '/login';
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Processing…';
    try {
      await addToCart(_product.id, _qty);
      window.location.hash = '/checkout';
    } catch {
      btn.disabled = false;
      btn.textContent = 'Buy Now';
      showToast('error', 'Error', 'Could not proceed. Please try again.');
    }
  });
}

function _bindTabs() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach(b => {
        b.classList.remove('active');
        b.setAttribute('aria-selected', 'false');
      });
      document.querySelectorAll('.tab-panel').forEach(panel => {
        panel.style.display = 'none';
      });
      btn.classList.add('active');
      btn.setAttribute('aria-selected', 'true');
      const panel = document.getElementById('tab-' + btn.dataset.tab);
      if (panel) panel.style.display = 'block';
    });
  });
}

async function _renderRelated() {
  const grid = document.getElementById('relatedGrid');
  if (!grid || !_product) return;

  try {
    const result = await getProducts({ category: _product.categorySlug, size: 5 });
    const products = (result.products || []).filter(p => p.id !== _product.id).slice(0, 4);

    if (!products.length) {
      grid.closest('section')?.remove();
      return;
    }

    grid.innerHTML = products.map(productCardHTML).join('');
    attachCardEvents(grid);
  } catch {
    grid.closest('section')?.remove();
  }
}

// ── Templates ─────────────────────────────────────────────────────

function _buildSkeleton() {
  return `
  <div id="productDetailMain">
    <div class="container" style="padding-top:var(--sp-4);padding-bottom:var(--sp-2)">
      <div class="skeleton" style="height:16px;width:220px;border-radius:4px"></div>
    </div>
    <div class="container" style="padding-bottom:var(--sp-12)">
      <div class="product-detail-grid">
        <div class="skeleton" style="aspect-ratio:1;border-radius:var(--r-lg)"></div>
        <div style="display:flex;flex-direction:column;gap:var(--sp-4)">
          <div class="skeleton" style="height:14px;width:120px"></div>
          <div class="skeleton" style="height:32px;width:80%"></div>
          <div class="skeleton" style="height:14px;width:60px"></div>
          <div class="skeleton" style="height:40px;width:140px"></div>
          <div class="skeleton" style="height:48px;width:100%"></div>
          <div class="skeleton" style="height:48px;width:100%"></div>
        </div>
      </div>
    </div>
  </div>`;
}

function _buildTemplate(p) {
  const pricePaise  = p.pricePaise  ?? p.price_paise  ?? 0;
  const mrpPaise    = p.mrpPaise    ?? p.mrp_paise;
  const stockQty    = p.stockQty    ?? p.stock_qty     ?? 0;
  const weightGrams = p.weightGrams ?? p.weight_grams;
  const categorySlug = p.categorySlug ?? p.category_slug ?? '';
  const categoryName = p.categoryName ?? p.category_name ?? (
    categorySlug === 'oils'   ? 'Cold Pressed Oils'  :
    categorySlug === 'masala' ? 'Masala Powders'      :
    categorySlug === 'flours' ? 'Flours & Grains'     : 'Products'
  );

  const hasDiscount = mrpPaise && mrpPaise > pricePaise;
  const discount    = hasDiscount ? discountPercent(mrpPaise, pricePaise) : 0;
  const inStock     = stockQty > 0;
  const images      = p.images?.length ? p.images : [];

  const thumbs = images.length > 1 ? images.map((src, i) => `
    <button class="gallery-thumb${i === 0 ? ' active' : ''}" data-src="${src}" aria-label="Product image ${i+1}">
      <img src="${src}" alt="${p.name}" loading="lazy" />
    </button>`).join('') : '';

  const mainImgHTML = images.length
    ? `<img id="mainProductImg" src="${images[0]}" alt="${p.name}" loading="eager" />`
    : `<div id="mainProductImg" style="width:100%;aspect-ratio:1;background:var(--clr-divider);display:flex;align-items:center;justify-content:center;font-size:4rem;border-radius:var(--r-lg)">🫙</div>`;

  return `
  <!-- Breadcrumb -->
  <div class="container" style="padding-top:var(--sp-4);padding-bottom:var(--sp-2)">
    <nav class="breadcrumb" aria-label="Breadcrumb">
      <a href="#/">Home</a>
      <span>›</span>
      <a href="#/products${categorySlug ? '?category=' + categorySlug : ''}">${categoryName}</a>
      <span>›</span>
      <span aria-current="page">${p.name}</span>
    </nav>
  </div>

  <!-- Product main -->
  <div class="container" style="padding-bottom:var(--sp-12)">
    <div class="product-detail-grid">

      <!-- Gallery -->
      <div class="product-gallery">
        <div class="gallery-main">
          ${mainImgHTML}
          ${p.tags?.includes('new')  ? '<span class="badge badge-new">New</span>'  : ''}
          ${hasDiscount ? `<span class="badge badge-sale">${discount}% off</span>` : ''}
        </div>
        ${images.length > 1 ? `<div class="gallery-thumbs">${thumbs}</div>` : ''}
      </div>

      <!-- Info -->
      <div class="product-info">
        <p style="font-size:var(--ts-sm);color:var(--clr-text-m);margin-bottom:var(--sp-2)">${categoryName}</p>
        <h1 style="font-size:var(--ts-2xl);margin-bottom:var(--sp-2)">${p.name}</h1>

        ${weightGrams ? `<p style="color:var(--clr-text-m);font-size:var(--ts-sm);margin-bottom:var(--sp-4)">${weightGrams}g</p>` : ''}

        <!-- Price -->
        <div style="display:flex;align-items:baseline;gap:var(--sp-3);flex-wrap:wrap;margin-bottom:var(--sp-5)">
          <span class="price-sale" style="font-size:var(--ts-2xl)">${formatPrice(pricePaise)}</span>
          ${hasDiscount ? `<span class="price-mrp">${formatPrice(mrpPaise)}</span>` : ''}
          ${hasDiscount ? `<span class="price-discount">${discount}% off</span>` : ''}
        </div>

        <!-- Stock status -->
        <div style="display:flex;align-items:center;gap:var(--sp-2);margin-bottom:var(--sp-5)">
          <span style="width:8px;height:8px;border-radius:50%;background:${inStock ? 'var(--clr-success)' : 'var(--clr-error)'}"></span>
          <span style="font-size:var(--ts-sm);color:${inStock ? 'var(--clr-success)' : 'var(--clr-error)'};font-weight:var(--fw-medium)">
            ${inStock ? `In Stock${stockQty < 10 ? ` — only ${stockQty} left` : ''}` : 'Out of Stock'}
          </span>
        </div>

        <!-- Qty stepper -->
        <div style="display:flex;align-items:center;gap:var(--sp-4);margin-bottom:var(--sp-5)">
          <label style="font-size:var(--ts-sm);font-weight:var(--fw-medium)">Quantity</label>
          <div class="qty-stepper">
            <button class="qty-btn" id="qtyMinus" aria-label="Decrease quantity" ${_qty <= 1 ? 'disabled' : ''}>−</button>
            <input type="number" id="qtyInput" class="qty-input" value="${_qty}" min="1" max="10" aria-label="Quantity" />
            <button class="qty-btn" id="qtyPlus" aria-label="Increase quantity" ${_qty >= 10 ? 'disabled' : ''}>+</button>
          </div>
        </div>

        <!-- CTAs -->
        <div style="display:flex;flex-direction:column;gap:var(--sp-3);margin-bottom:var(--sp-6)">
          <button class="btn btn-primary btn-lg" id="addToCartBtn" ${!inStock ? 'disabled' : ''} style="width:100%">
            ${inStock ? 'Add to Cart' : 'Out of Stock'}
          </button>
          ${inStock ? `<button class="btn btn-secondary btn-lg" id="buyNowBtn" style="width:100%">Buy Now</button>` : ''}
        </div>

        <!-- Trust badges -->
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--sp-3);padding:var(--sp-4);background:var(--clr-bg);border-radius:var(--r-lg)">
          ${[
            { icon:'✓', text:'100% Natural' },
            { icon:'✓', text:'No Preservatives' },
            { icon:'🚚', text:'Free delivery ₹499+' },
            { icon:'↩', text:'Easy Returns' },
          ].map(b => `
            <div style="display:flex;align-items:center;gap:var(--sp-2);font-size:var(--ts-xs);color:var(--clr-text-m)">
              <span style="color:var(--clr-primary);font-weight:bold">${b.icon}</span>
              ${b.text}
            </div>`).join('')}
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs" style="margin-top:var(--sp-12)">
      <div class="tab-list" role="tablist">
        <button class="tab-btn active" role="tab" aria-selected="true"  data-tab="description">Description</button>
        <button class="tab-btn"        role="tab" aria-selected="false" data-tab="ingredients">Ingredients</button>
        <button class="tab-btn"        role="tab" aria-selected="false" data-tab="usage">How to Use</button>
      </div>

      <div class="tab-panel" id="tab-description" style="padding:var(--sp-6) 0">
        <p style="line-height:1.8;color:var(--clr-text-b)">${p.description || 'No description available.'}</p>
      </div>
      <div class="tab-panel" id="tab-ingredients" style="padding:var(--sp-6) 0;display:none">
        <p style="line-height:1.8;color:var(--clr-text-b)">${p.ingredients || 'No ingredient information available.'}</p>
      </div>
      <div class="tab-panel" id="tab-usage" style="padding:var(--sp-6) 0;display:none">
        ${categorySlug === 'oils' ? `
        <ul style="line-height:2;color:var(--clr-text-b);padding-left:var(--sp-5)">
          <li>Ideal for cooking, tempering, and frying.</li>
          <li>Store in a cool, dry place away from direct sunlight.</li>
          <li>Best consumed within 6 months of opening.</li>
          <li>Can also be used for hair and skin care.</li>
        </ul>` : `
        <ul style="line-height:2;color:var(--clr-text-b);padding-left:var(--sp-5)">
          <li>Store in an airtight container after opening.</li>
          <li>Keep in a cool, dry place away from moisture.</li>
          <li>Best consumed within 3 months of opening.</li>
          <li>No artificial preservatives — freshness maintained by proper storage.</li>
        </ul>`}
      </div>
    </div>
  </div>

  <!-- Related Products -->
  <section class="section" style="background:var(--clr-bg)">
    <div class="container">
      <div class="section-header">
        <h2 class="section-title">You May Also Like</h2>
        <a href="#/products${categorySlug ? '?category=' + categorySlug : ''}" class="section-link">View All →</a>
      </div>
      <div class="product-grid" id="relatedGrid"></div>
    </div>
  </section>
  `;
}

export default page;
