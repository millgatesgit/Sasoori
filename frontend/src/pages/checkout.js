/**
 * SASOORI — Checkout Page
 * Route: #/checkout  (requires auth)
 *
 * Flow:
 *  1. Load cart + addresses in parallel
 *  2. Address section: pick existing or add new
 *  3. Order summary sidebar
 *  4. "Place Order" → backend creates Razorpay order → open Razorpay modal
 *  5. On success → verify payment → redirect to #/orders
 */

import { getCart }                           from '../api/cartApi.js';
import { getAddresses, createAddress,
         placeOrder, verifyPayment,
         getConfig }                          from '../api/orderApi.js';
import { formatPrice }                        from '../utils/formatters.js';
import { showToast }                          from '../components/toast.js';
import store                                  from '../store.js';

const SHIPPING_THRESHOLD = 49900;
const SHIPPING_CHARGE    = 4900;

// ── Module state ──────────────────────────────────────────────────────────

let _state = {
  cartItems:      [],
  subtotal:       0,
  shipping:       0,
  addresses:      [],
  selectedAddrId: null,
  showAddForm:    false,
  placing:        false,
  razorpayKeyId:  null,
};

// ── Page object ───────────────────────────────────────────────────────────

const page = {
  meta: () => ({ title: 'Checkout', description: 'Complete your order.' }),

  render(container) {
    container.innerHTML = _shell();
  },

  async onMount() {
    if (!store.isLoggedIn()) {
      window.location.hash = '/login?next=/checkout';
      return;
    }
    await _boot();
    _bindAll();
  },

  onDestroy() {
    _state = {
      cartItems: [], subtotal: 0, shipping: 0,
      addresses: [], selectedAddrId: null,
      showAddForm: false, placing: false, razorpayKeyId: null,
    };
  },
};

export default page;

// ── Bootstrap ─────────────────────────────────────────────────────────────

async function _boot() {
  _setLoading(true);
  try {
    const [cartData, addrData, cfg] = await Promise.all([
      getCart(),
      getAddresses(),
      getConfig(),
    ]);

    _state.cartItems     = cartData?.items || [];
    _state.subtotal      = cartData?.subtotalPaise || 0;
    _state.shipping      = _state.subtotal >= SHIPPING_THRESHOLD ? 0 : SHIPPING_CHARGE;
    _state.addresses     = Array.isArray(addrData) ? addrData : [];
    _state.razorpayKeyId = cfg?.razorpayKeyId || null;

    // Pre-select default address
    const def = _state.addresses.find(a => a.isDefault) || _state.addresses[0];
    if (def) _state.selectedAddrId = def.id;

    _render();
  } catch (err) {
    _setError('Failed to load checkout. ' + (err.message || ''));
  } finally {
    _setLoading(false);
  }
}

// ── Render ────────────────────────────────────────────────────────────────

function _render() {
  const addrSection  = document.getElementById('coAddressSection');
  const summaryEl    = document.getElementById('coSummary');
  const ctaBar       = document.getElementById('coCtaBar');

  if (!addrSection || !summaryEl) return;

  if (_state.cartItems.length === 0) {
    addrSection.innerHTML = `
      <div class="co-empty">
        <p>Your cart is empty.</p>
        <a href="#/products" class="btn btn-primary">Browse Products</a>
      </div>`;
    if (ctaBar) ctaBar.style.display = 'none';
    return;
  }

  addrSection.innerHTML = _addressSection();
  summaryEl.innerHTML   = _orderSummary();
  if (ctaBar) ctaBar.innerHTML = _ctaBar();

  _bindAddressEvents();
}

// ── Address section ───────────────────────────────────────────────────────

function _addressSection() {
  const list = _state.addresses.map(a => `
    <label class="addr-card ${a.id === _state.selectedAddrId ? 'selected' : ''}">
      <input type="radio" name="address" value="${a.id}"
             ${a.id === _state.selectedAddrId ? 'checked' : ''} />
      <div class="addr-card-body">
        <div class="addr-card-name">${_esc(a.name)}
          ${a.isDefault ? '<span class="badge-default">Default</span>' : ''}
        </div>
        <div class="addr-card-detail">${_esc(a.line1)}${a.line2 ? ', ' + _esc(a.line2) : ''}</div>
        <div class="addr-card-detail">${_esc(a.city)}, ${_esc(a.state)} — ${_esc(a.pincode)}</div>
        <div class="addr-card-phone">${_esc(a.phone)}</div>
      </div>
    </label>`).join('');

  return `
    <h2 class="co-section-title">Delivery Address</h2>
    <div class="addr-list" id="addrList">
      ${list || '<p class="addr-none">No saved addresses.</p>'}
    </div>

    <button class="btn btn-outline co-add-addr-btn" id="toggleAddForm" type="button">
      ${_state.showAddForm ? '✕ Cancel' : '+ Add New Address'}
    </button>

    <form id="newAddrForm" class="new-addr-form ${_state.showAddForm ? '' : 'hidden'}"
          novalidate>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">Full Name *</label>
          <input class="form-input" name="name" required placeholder="Priya Sharma" />
        </div>
        <div class="form-group">
          <label class="form-label">Phone *</label>
          <input class="form-input" name="phone" required placeholder="+919876543210" />
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">Address Line 1 *</label>
        <input class="form-input" name="line1" required placeholder="House no, Street name" />
      </div>
      <div class="form-group">
        <label class="form-label">Address Line 2</label>
        <input class="form-input" name="line2" placeholder="Apartment, landmark (optional)" />
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">City *</label>
          <input class="form-input" name="city" required placeholder="Mumbai" />
        </div>
        <div class="form-group">
          <label class="form-label">State *</label>
          <input class="form-input" name="state" required placeholder="Maharashtra" />
        </div>
        <div class="form-group form-group--pincode">
          <label class="form-label">Pincode *</label>
          <input class="form-input" name="pincode" required maxlength="6"
                 pattern="\\d{6}" placeholder="400001" />
        </div>
      </div>
      <label class="form-checkbox">
        <input type="checkbox" name="isDefault" />
        Set as default address
      </label>
      <button type="submit" class="btn btn-primary" id="saveAddrBtn">Save Address</button>
      <span class="form-error hidden" id="addrFormError"></span>
    </form>`;
}

// ── Order summary ─────────────────────────────────────────────────────────

function _orderSummary() {
  const lines = _state.cartItems.map(i => `
    <div class="co-item">
      <img src="${_esc(i.imageUrl || 'assets/images/placeholder.png')}"
           alt="${_esc(i.productName)}" class="co-item-img" />
      <div class="co-item-info">
        <div class="co-item-name">${_esc(i.productName)}</div>
        <div class="co-item-meta">Qty ${i.quantity} × ${formatPrice(i.pricePaise)}</div>
      </div>
      <div class="co-item-total">${formatPrice(i.quantity * i.pricePaise)}</div>
    </div>`).join('');

  const total = _state.subtotal + _state.shipping;

  return `
    <h2 class="co-section-title">Order Summary</h2>
    <div class="co-items">${lines}</div>
    <div class="co-divider"></div>
    <div class="co-summary-row">
      <span>Subtotal</span>
      <span>${formatPrice(_state.subtotal)}</span>
    </div>
    <div class="co-summary-row">
      <span>Delivery</span>
      <span class="${_state.shipping === 0 ? 'co-free-shipping' : ''}">
        ${_state.shipping === 0 ? 'FREE' : formatPrice(_state.shipping)}
      </span>
    </div>
    ${_state.shipping > 0 ? `
    <div class="co-shipping-hint">
      Add ${formatPrice(SHIPPING_THRESHOLD - _state.subtotal)} more for free delivery
    </div>` : ''}
    <div class="co-divider"></div>
    <div class="co-summary-row co-summary-total">
      <span>Total</span>
      <span>${formatPrice(total)}</span>
    </div>
    <!-- Desktop pay button (hidden on mobile via CSS; mobile uses sticky bar) -->
    <button class="btn btn-primary co-pay-btn co-pay-desktop" id="placeOrderBtnDesktop"
            ${!_state.selectedAddrId ? 'disabled' : ''}>
      Pay ${formatPrice(total)}
    </button>
    ${!_state.selectedAddrId
      ? '<p class="co-addr-warning">Select a delivery address above</p>'
      : ''}`;
}

// ── CTA bar ───────────────────────────────────────────────────────────────

function _ctaBar() {
  const total = _state.subtotal + _state.shipping;
  return `
    <div class="co-cta-inner">
      <div class="co-cta-total">
        <span class="co-cta-label">Total</span>
        <span class="co-cta-amount">${formatPrice(total)}</span>
      </div>
      <button class="btn btn-primary co-pay-btn" id="placeOrderBtn"
              ${!_state.selectedAddrId ? 'disabled' : ''}>
        Pay ${formatPrice(total)}
      </button>
    </div>
    ${!_state.selectedAddrId
      ? '<p class="co-addr-warning">Please select a delivery address to continue</p>'
      : ''}`;
}

// ── Event binding ─────────────────────────────────────────────────────────

function _bindAll() {
  document.addEventListener('click', _onDocClick);
}

function _bindAddressEvents() {
  // Radio change
  document.querySelectorAll('input[name="address"]').forEach(radio => {
    radio.addEventListener('change', e => {
      _state.selectedAddrId = parseInt(e.target.value, 10);
      document.querySelectorAll('.addr-card').forEach(c => c.classList.remove('selected'));
      e.target.closest('.addr-card').classList.add('selected');
      // Refresh CTA bar
      const ctaBar = document.getElementById('coCtaBar');
      if (ctaBar) ctaBar.innerHTML = _ctaBar();
      _bindCtaEvents();
    });
  });

  // Toggle add-address form
  const toggle = document.getElementById('toggleAddForm');
  if (toggle) {
    toggle.addEventListener('click', () => {
      _state.showAddForm = !_state.showAddForm;
      toggle.textContent = _state.showAddForm ? '✕ Cancel' : '+ Add New Address';
      const form = document.getElementById('newAddrForm');
      if (form) form.classList.toggle('hidden');
    });
  }

  // Save address form
  const form = document.getElementById('newAddrForm');
  if (form) {
    form.addEventListener('submit', async e => {
      e.preventDefault();
      await _saveAddress(form);
    });
  }

  _bindCtaEvents();
}

function _bindCtaEvents() {
  document.getElementById('placeOrderBtn')?.addEventListener('click', _onPlaceOrder);
  document.getElementById('placeOrderBtnDesktop')?.addEventListener('click', _onPlaceOrder);
}

function _onDocClick(e) {
  // Nothing global needed yet
}

// ── Save address ──────────────────────────────────────────────────────────

async function _saveAddress(form) {
  const btn = form.querySelector('#saveAddrBtn');
  const errEl = form.querySelector('#addrFormError');

  const data = {
    name:      form.name.value.trim(),
    phone:     form.phone.value.trim(),
    line1:     form.line1.value.trim(),
    line2:     form.line2.value.trim() || null,
    city:      form.city.value.trim(),
    state:     form.state.value.trim(),
    pincode:   form.pincode.value.trim(),
    isDefault: form.isDefault.checked,
  };

  // Basic validation
  const missing = ['name','phone','line1','city','state','pincode']
    .find(f => !data[f]);
  if (missing) {
    _showFormError(errEl, `Please fill in all required fields.`);
    return;
  }
  if (!/^\d{6}$/.test(data.pincode)) {
    _showFormError(errEl, 'Pincode must be exactly 6 digits.');
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Saving…';
  _hideFormError(errEl);

  try {
    const addr = await createAddress(data);
    _state.addresses.push(addr);
    _state.selectedAddrId = addr.id;
    _state.showAddForm = false;
    showToast('success', 'Address saved', 'Your delivery address has been added.');
    _render();
    _bindAddressEvents();
  } catch (err) {
    _showFormError(errEl, err.message || 'Failed to save address.');
    btn.disabled = false;
    btn.textContent = 'Save Address';
  }
}

// ── Place order ───────────────────────────────────────────────────────────

async function _onPlaceOrder() {
  if (_state.placing) return;
  if (!_state.selectedAddrId) {
    showToast('error', 'Select address', 'Please choose a delivery address.');
    return;
  }

  const btn = document.getElementById('placeOrderBtn');
  _state.placing = true;
  if (btn) { btn.disabled = true; btn.textContent = 'Creating order…'; }

  try {
    // 1. Create order on backend → get Razorpay order_id
    const order = await placeOrder(_state.selectedAddrId, '');

    // 2. Open Razorpay checkout
    await _openRazorpay(order);

  } catch (err) {
    showToast('error', 'Order failed', err.message || 'Could not place your order.');
    _state.placing = false;
    if (btn) { btn.disabled = false; btn.textContent = `Pay ${formatPrice(_state.subtotal + _state.shipping)}`; }
  }
}

function _openRazorpay(order) {
  return new Promise((resolve, reject) => {
    if (!window.Razorpay) {
      reject(new Error('Payment gateway not loaded. Please refresh and try again.'));
      return;
    }

    const user = store.get('user') || {};
    const options = {
      key:         _state.razorpayKeyId,
      amount:      order.totalPaise,
      currency:    order.currency || 'INR',
      name:        'Sasoori',
      description: 'Natural Products',
      image:       'assets/images/saroori-logo.png',
      order_id:    order.razorpayOrderId,
      prefill: {
        name:    user.name  || '',
        email:   user.email || '',
        contact: user.phone || '',
      },
      theme: { color: '#00923F' },

      handler: async function(response) {
        const btn = document.getElementById('placeOrderBtn');
        if (btn) { btn.textContent = 'Verifying payment…'; }
        try {
          await verifyPayment(
            order.orderId,
            response.razorpay_payment_id,
            response.razorpay_signature
          );
          showToast('success', 'Payment successful!', 'Your order has been placed.');
          // Redirect to orders page after short delay
          setTimeout(() => {
            window.location.hash = '/orders';
          }, 1200);
          resolve();
        } catch (err) {
          showToast('error', 'Verification failed', 'Payment received but verification failed. Contact support.');
          reject(err);
        }
      },

      modal: {
        ondismiss: () => {
          _state.placing = false;
          const btn = document.getElementById('placeOrderBtn');
          if (btn) {
            btn.disabled    = false;
            btn.textContent = `Pay ${formatPrice(_state.subtotal + _state.shipping)}`;
          }
          showToast('info', 'Payment cancelled', 'Your order has not been placed.');
          reject(new Error('Payment modal closed'));
        },
      },
    };

    const rzp = new window.Razorpay(options);
    rzp.on('payment.failed', function(response) {
      showToast('error', 'Payment failed', response.error?.description || 'Payment was declined.');
      _state.placing = false;
      const btn = document.getElementById('placeOrderBtn');
      if (btn) {
        btn.disabled    = false;
        btn.textContent = `Pay ${formatPrice(_state.subtotal + _state.shipping)}`;
      }
    });
    rzp.open();
  });
}

// ── HTML helpers ──────────────────────────────────────────────────────────

function _shell() {
  return `
    <div class="checkout-page container">
      <h1 class="co-page-title">Checkout</h1>
      <div id="coError" class="co-error hidden"></div>
      <div class="co-layout">
        <!-- Left: address + loading state -->
        <div class="co-left">
          <div id="coAddressSection">
            ${_skeletonAddr()}
          </div>
        </div>
        <!-- Right: order summary -->
        <div class="co-right">
          <div id="coSummary">
            ${_skeletonSummary()}
          </div>
        </div>
      </div>
      <!-- Sticky CTA bar -->
      <div class="co-cta-bar" id="coCtaBar"></div>
    </div>`;
}

function _skeletonAddr() {
  return `<div class="skeleton-block" style="height:200px;border-radius:var(--radius-lg)"></div>`;
}

function _skeletonSummary() {
  return `<div class="skeleton-block" style="height:300px;border-radius:var(--radius-lg)"></div>`;
}

function _setLoading(on) {
  // Skeletons are in the initial shell; real content replaces on _render()
}

function _setError(msg) {
  const el = document.getElementById('coError');
  if (!el) return;
  el.textContent = msg;
  el.classList.remove('hidden');
}

function _showFormError(el, msg) {
  if (!el) return;
  el.textContent = msg;
  el.classList.remove('hidden');
}

function _hideFormError(el) {
  if (!el) return;
  el.textContent = '';
  el.classList.add('hidden');
}

function _esc(s) {
  if (!s) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
