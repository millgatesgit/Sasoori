/**
 * SASOORI — Product Card Component
 * Returns an HTML string. Attach events after inserting into DOM.
 */

import { formatPrice, discountPercent } from '../utils/formatters.js';
import { addToCart } from '../api/cartApi.js';
import { showToast } from './toast.js';
import store from '../store.js';

/**
 * Render a product card HTML string
 * @param {object} product
 * @returns {string} HTML
 */
export function productCardHTML(product) {
  // Support both camelCase (API) and snake_case (legacy mock data)
  const id         = product.id;
  const slug       = product.slug;
  const name       = product.name;
  const weightGrams = product.weightGrams ?? product.weight_grams;
  const pricePaise  = product.pricePaise  ?? product.price_paise;
  const mrpPaise    = product.mrpPaise    ?? product.mrp_paise;
  const images     = product.images;
  const isFeatured = product.isFeatured  ?? product.is_featured;
  const stockQty   = product.stockQty    ?? product.stock_qty;
  const tags       = product.tags || [];

  const imgSrc  = images?.[0] || '';
  const discount = discountPercent(mrpPaise, pricePaise);
  const inStock  = stockQty > 0;
  const isNew    = tags.includes('new');
  const isLow    = stockQty > 0 && stockQty <= 5;

  const badges = [];
  if (isNew)       badges.push(`<span class="badge badge-new">NEW</span>`);
  if (discount)    badges.push(`<span class="badge badge-sale">${discount}% OFF</span>`);
  if (isFeatured && !isNew) badges.push(`<span class="badge badge-best">BEST</span>`);
  if (!inStock)    badges.push(`<span class="badge badge-out">Out of Stock</span>`);
  if (isLow)       badges.push(`<span class="badge badge-low">Only ${stockQty} left</span>`);

  return `
    <article class="product-card" data-product-id="${id}" data-product-slug="${slug}">
      <a href="#/product/${slug}" aria-label="${_esc(name)}">
        <div class="product-card-img-wrap">
          ${imgSrc
            ? `<img src="${_esc(imgSrc)}" alt="${_esc(name)} ${weightGrams ? weightGrams + 'g' : ''}"
                    loading="lazy" decoding="async" />`
            : `<div class="product-card-img-placeholder">🫙</div>`}
          ${badges.length ? `<div class="product-card-badges">${badges.join('')}</div>` : ''}
        </div>

        <div class="product-card-body">
          <div class="product-card-name">${_esc(name)}</div>
          ${weightGrams ? `<div class="product-card-weight">${weightGrams}g</div>` : ''}
          <div class="product-card-price">
            <span class="price price-sale">${formatPrice(pricePaise)}</span>
            ${mrpPaise && mrpPaise > pricePaise
              ? `<span class="price price-mrp">${formatPrice(mrpPaise)}</span>
                 <span class="price-discount">${discount}% off</span>`
              : ''}
          </div>
        </div>
      </a>

      <div class="product-card-footer">
        <button
          class="btn btn-primary btn-full btn-sm add-to-cart-btn"
          data-product-id="${id}"
          ${!inStock ? 'disabled' : ''}
          aria-label="Add ${_esc(name)} to cart"
        >
          ${inStock ? '+ Add to Cart' : 'Out of Stock'}
        </button>
      </div>
    </article>
  `;
}

/**
 * Attach add-to-cart event listeners to all cards in a container
 * @param {HTMLElement} container
 */
export function attachCardEvents(container) {
  container.querySelectorAll('.add-to-cart-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.preventDefault();
      e.stopPropagation();

      const productId = btn.dataset.productId;
      if (!productId) return;

      // Auth check
      if (!store.isLoggedIn()) {
        showToast('info', 'Sign in required', 'Please sign in to add items to cart.');
        setTimeout(() => { window.location.hash = '/login'; }, 1200);
        return;
      }

      // Optimistic UI
      const original = btn.textContent.trim();
      btn.disabled = true;
      btn.classList.add('loading');
      btn.textContent = '';

      try {
        await addToCart(productId, 1);

        btn.classList.remove('loading');
        btn.classList.add('btn-added');
        btn.textContent = '✓ Added';
        btn.disabled = false;

        showToast('success', 'Added to cart!', 'Item added to your cart.');

        // Bump cart badge animation
        const badge = document.getElementById('cartBadge');
        if (badge) {
          badge.classList.add('bump');
          setTimeout(() => badge.classList.remove('bump'), 300);
        }

        // Revert after 1.5s
        setTimeout(() => {
          btn.classList.remove('btn-added');
          btn.textContent = original;
        }, 1500);

      } catch (err) {
        btn.classList.remove('loading');
        btn.textContent = original;
        btn.disabled = false;
        showToast('error', 'Failed to add', err.message || 'Please try again.');
      }
    });
  });
}

/**
 * Skeleton cards for loading state
 * @param {number} count
 * @returns {string}
 */
export function skeletonCards(count = 8) {
  return Array(count).fill(0).map(() => `
    <div class="skeleton-card">
      <div class="skeleton skeleton-img"></div>
      <div class="skeleton skeleton-text" style="width:85%;margin-top:var(--sp-3)"></div>
      <div class="skeleton skeleton-text skeleton-text-sm"></div>
      <div class="skeleton skeleton-text" style="width:45%"></div>
      <div class="skeleton skeleton-btn"></div>
    </div>
  `).join('');
}

function _esc(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
