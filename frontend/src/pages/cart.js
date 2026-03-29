/**
 * SASOORI — Cart Page
 * Route: #/cart
 */

import { getCart, updateCartItem, removeCartItem } from '../api/cartApi.js';
import { formatPrice } from '../utils/formatters.js';
import { showToast } from '../components/toast.js';
import store from '../store.js';

const SHIPPING_THRESHOLD = 49900; // Free shipping above ₹499
const SHIPPING_CHARGE    = 4900;  // ₹49 shipping

const page = {
  meta: () => ({ title: 'My Cart', description: 'Review your cart and proceed to checkout.' }),

  async render(container) {
    container.innerHTML = _buildShell();
  },

  async onMount() {
    await _loadCart();
  },

  onDestroy() {},
};

async function _loadCart() {
  const inner = document.getElementById('cartInner');
  if (!inner) return;

  if (!store.isLoggedIn()) {
    _renderGuestCart(inner);
    return;
  }

  inner.innerHTML = _skeleton();

  try {
    const data = await getCart();
    const items = data?.items || [];
    _renderCart(inner, items);
  } catch {
    // Fallback: show items from store state (local cache)
    const storeCart = store.get('cart') || [];
    _renderCart(inner, storeCart);
  }
}

function _renderGuestCart(inner) {
  // For guests, show mock cart from store state or empty state
  const storeCart = store.get('cart') || [];
  if (storeCart.length) {
    _renderCart(inner, storeCart);
  } else {
    _renderEmpty(inner);
  }
}

function _renderEmpty(inner) {
  inner.innerHTML = `
    <div style="text-align:center;padding:var(--sp-20) var(--sp-4)">
      <div style="font-size:4rem;margin-bottom:var(--sp-4)">🛒</div>
      <h2 style="margin-bottom:var(--sp-3)">Your cart is empty</h2>
      <p style="color:var(--clr-text-m);margin-bottom:var(--sp-8)">Discover our pure, natural products and add something you love.</p>
      <a href="#/products" class="btn btn-primary btn-lg">Start Shopping</a>
    </div>`;
}

function _renderCart(inner, items) {
  if (!items.length) {
    _renderEmpty(inner);
    return;
  }

  const enriched = items.map(item => ({
    productId:   item.productId   || item.product_id,
    name:        item.productName || item.product_name || 'Product',
    image:       item.imageUrl    || item.product_image || '',
    weight:      item.weightGrams || item.weight_grams,
    // Support API camelCase, legacy snake_case, AND already-enriched items (price_paise)
    price_paise: item.pricePaise  || item.unit_price_paise || item.price_paise || 0,
    quantity:    item.quantity    || 1,
    slug:        item.productSlug || item.product_slug || '',
  }));

  const subtotal = enriched.reduce((sum, i) => sum + i.price_paise * i.quantity, 0);
  const shipping  = subtotal >= SHIPPING_THRESHOLD ? 0 : SHIPPING_CHARGE;
  const total     = subtotal + shipping;
  const totalQty  = enriched.reduce((sum, i) => sum + i.quantity, 0);

  inner.innerHTML = `
  <div class="cart-layout">

    <!-- Items list -->
    <div class="cart-items-col">
      <h1 style="font-size:var(--ts-xl);margin-bottom:var(--sp-5)">My Cart <span style="color:var(--clr-text-m);font-size:var(--ts-base);font-weight:400">(${totalQty} item${totalQty !== 1 ? 's' : ''})</span></h1>

      <div id="cartItemsList">
        ${enriched.map(item => _itemHTML(item)).join('')}
      </div>

      <div style="padding-top:var(--sp-4)">
        <a href="#/products" style="color:var(--clr-primary);font-size:var(--ts-sm);display:inline-flex;align-items:center;gap:var(--sp-2)">
          ← Continue Shopping
        </a>
      </div>
    </div>

    <!-- Order summary -->
    <aside class="cart-summary-col">
      <div class="order-summary-card" id="orderSummaryCard">
        <h3 style="font-size:var(--ts-lg);margin-bottom:var(--sp-5)">Order Summary</h3>

        <div class="summary-row">
          <span>Subtotal (${totalQty} items)</span>
          <span>${formatPrice(subtotal)}</span>
        </div>
        <div class="summary-row">
          <span>Shipping</span>
          <span>${shipping === 0 ? '<span style="color:var(--clr-success)">FREE</span>' : formatPrice(shipping)}</span>
        </div>
        ${shipping > 0 ? `
        <div style="font-size:var(--ts-xs);color:var(--clr-text-m);padding:var(--sp-2) 0 var(--sp-3)">
          Add <strong>${formatPrice(SHIPPING_THRESHOLD - subtotal)}</strong> more for free delivery
          <div style="height:4px;background:var(--clr-divider);border-radius:var(--r-full);margin-top:var(--sp-2)">
            <div style="height:100%;width:${Math.min(100, Math.round(subtotal / SHIPPING_THRESHOLD * 100))}%;background:var(--clr-primary);border-radius:var(--r-full)"></div>
          </div>
        </div>` : `
        <div style="font-size:var(--ts-xs);color:var(--clr-success);padding:var(--sp-1) 0 var(--sp-3)">
          🎉 You've unlocked free delivery!
        </div>`}
        <div class="summary-divider"></div>
        <div class="summary-row summary-total">
          <span>Total</span>
          <span>${formatPrice(total)}</span>
        </div>

        <a href="${store.isLoggedIn() ? '#/checkout' : '#/login'}" class="btn btn-primary btn-lg" style="width:100%;margin-top:var(--sp-5);text-align:center">
          ${store.isLoggedIn() ? 'Proceed to Checkout →' : 'Sign In to Checkout'}
        </a>

        <div style="display:flex;align-items:center;justify-content:center;gap:var(--sp-2);margin-top:var(--sp-4);font-size:var(--ts-xs);color:var(--clr-text-m)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          Secure SSL checkout
        </div>
      </div>
    </aside>
  </div>
  `;

  _bindCartEvents(enriched);
}

function _itemHTML(item) {
  return `
  <div class="cart-item" data-product-id="${item.productId}" id="cart-item-${item.productId}">
    <a href="#/product/${item.slug}" class="cart-item-img-link">
      ${item.image
        ? `<img src="${item.image}" alt="${item.name}" loading="lazy" class="cart-item-img" />`
        : `<div class="cart-item-img" style="background:var(--clr-divider);display:flex;align-items:center;justify-content:center;font-size:1.5rem">🫙</div>`
      }
    </a>
    <div class="cart-item-info">
      <a href="#/product/${item.slug}" class="cart-item-name">${item.name}</a>
      ${item.weight ? `<p class="cart-item-meta">${item.weight}g</p>` : ''}
      <div class="cart-item-price">${formatPrice(item.price_paise)}</div>
    </div>
    <div class="cart-item-controls">
      <div class="qty-stepper">
        <button class="qty-btn cart-qty-minus" data-id="${item.productId}" aria-label="Decrease" ${item.quantity <= 1 ? 'disabled' : ''}>−</button>
        <span class="qty-display">${item.quantity}</span>
        <button class="qty-btn cart-qty-plus"  data-id="${item.productId}" aria-label="Increase" ${item.quantity >= 10 ? 'disabled' : ''}>+</button>
      </div>
      <div class="cart-item-total">${formatPrice(item.price_paise * item.quantity)}</div>
      <button class="cart-remove-btn" data-id="${item.productId}" aria-label="Remove ${item.name}">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/></svg>
      </button>
    </div>
  </div>`;
}

function _bindCartEvents(items) {
  // Qty decrease
  document.querySelectorAll('.cart-qty-minus').forEach(btn => {
    btn.addEventListener('click', () => _changeQty(btn.dataset.id, -1, items));
  });
  // Qty increase
  document.querySelectorAll('.cart-qty-plus').forEach(btn => {
    btn.addEventListener('click', () => _changeQty(btn.dataset.id, 1, items));
  });
  // Remove
  document.querySelectorAll('.cart-remove-btn').forEach(btn => {
    btn.addEventListener('click', () => _removeItem(btn.dataset.id, items));
  });
}

const _qtyBusy = new Set(); // prevents concurrent qty updates per product

async function _changeQty(productId, delta, items) {
  if (_qtyBusy.has(productId)) return;
  const item = items.find(i => i.productId === productId);
  if (!item) return;
  const newQty = item.quantity + delta;
  if (newQty < 1 || newQty > 10) return;

  _qtyBusy.add(productId);
  try {
    await updateCartItem(productId, newQty);
    item.quantity = newQty;
    const inner = document.getElementById('cartInner');
    if (inner) _renderCart(inner, items);
  } catch (err) {
    showToast('error', 'Error', err.message || 'Could not update cart.');
  } finally {
    _qtyBusy.delete(productId);
  }
}

async function _removeItem(productId, items) {
  if (_qtyBusy.has(productId)) return;
  const row = document.getElementById(`cart-item-${productId}`);
  if (row) { row.style.opacity = '0.4'; row.style.pointerEvents = 'none'; }

  _qtyBusy.add(productId);
  try {
    await removeCartItem(productId);
    showToast('success', 'Removed', 'Item removed from cart.');
    // Remove from local list and re-render directly (no skeleton flash)
    const remaining = items.filter(i => i.productId !== productId);
    const inner = document.getElementById('cartInner');
    if (inner) _renderCart(inner, remaining);
  } catch (err) {
    if (row) { row.style.opacity = ''; row.style.pointerEvents = ''; }
    showToast('error', 'Error', err.message || 'Could not remove item.');
  } finally {
    _qtyBusy.delete(productId);
  }
}

function _skeleton() {
  return Array(3).fill(0).map(() => `
    <div class="cart-item">
      <div class="skeleton" style="width:80px;height:80px;border-radius:var(--r-md)"></div>
      <div style="flex:1;display:flex;flex-direction:column;gap:var(--sp-2)">
        <div class="skeleton" style="height:16px;width:60%"></div>
        <div class="skeleton" style="height:12px;width:30%"></div>
        <div class="skeleton" style="height:16px;width:20%"></div>
      </div>
    </div>`).join('');
}

function _buildShell() {
  return `
  <div class="container" style="padding-top:var(--sp-8);padding-bottom:var(--sp-12)">
    <div id="cartInner">${_skeleton()}</div>
  </div>`;
}

export default page;
