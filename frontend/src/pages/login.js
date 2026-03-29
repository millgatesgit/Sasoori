/**
 * SASOORI — Login Page
 * Route: #/login
 * Google OAuth2 PKCE flow + OTP phone login.
 */

import store from '../store.js';
import { showToast } from '../components/toast.js';
import { sendOtp, verifyOtp, testLogin } from '../api/authApi.js'; // implements: AC20

const IS_DEV = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

const GOOGLE_CLIENT_ID  = window.SASOORI_CONFIG?.GOOGLE_CLIENT_ID || '';
const OAUTH_REDIRECT    = window.location.origin + '/';
const SCOPE             = 'openid email profile';

const page = {
  meta: () => ({
    title: 'Sign In',
    description: 'Sign in to Sasoori with your Google account.',
  }),

  render(container) {
    // Redirect if already logged in
    if (store.isLoggedIn()) {
      window.location.hash = '/';
      return;
    }
    container.innerHTML = LOGIN_TEMPLATE;
  },

  onMount() {
    _bindGoogle();
    _bindGuest();
    _bindTabs();        // implements: AC1, AC2
    _bindOtp();         // implements: AC3–AC16
    _bindDevTestLogin();
  },

  onDestroy() {
    _stopCountdown();                        // implements: AC17
    _otpState.phase    = 'idle';
    _otpState.phone    = '';
    _otpState.secondsLeft = 0;
  },
};

function _bindGoogle() {
  const btn = document.getElementById('googleLoginBtn');
  if (!btn) return;

  btn.addEventListener('click', () => {
    if (!GOOGLE_CLIENT_ID) {
      showToast('error', 'Configuration error', 'Google login is not configured for this environment.');
      return;
    }
    _startGoogleOAuth();
  });
}

function _bindGuest() {
  const btn = document.getElementById('guestBtn');
  btn?.addEventListener('click', () => {
    window.location.hash = '/';
  });
}

function _startGoogleOAuth() {
  // PKCE: generate code_verifier and code_challenge
  const verifier  = _generateVerifier();
  const state     = _generateState();

  sessionStorage.setItem('oauth_verifier', verifier);
  sessionStorage.setItem('oauth_state',    state);

  _generateChallenge(verifier).then(challenge => {
    const params = new URLSearchParams({
      client_id:             GOOGLE_CLIENT_ID,
      redirect_uri:          OAUTH_REDIRECT,
      response_type:         'code',
      scope:                 SCOPE,
      state,
      code_challenge:        challenge,
      code_challenge_method: 'S256',
      access_type:           'offline',
      prompt:                'consent',
    });

    window.location.href = `https://accounts.google.com/o/oauth2/v2/auth?${params}`;
  });
}

function _generateVerifier() {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return btoa(String.fromCharCode(...array)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'');
}

function _generateState() {
  const array = new Uint8Array(16);
  crypto.getRandomValues(array);
  return btoa(String.fromCharCode(...array)).replace(/[^a-zA-Z0-9]/g,'');
}

async function _generateChallenge(verifier) {
  const encoder = new TextEncoder();
  const data    = encoder.encode(verifier);
  const digest  = await crypto.subtle.digest('SHA-256', data);
  const bytes   = new Uint8Array(digest);
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'');
}

// ── OTP state ────────────────────────────────────────────────────

// implements: AC6, AC14, AC17
let _otpState = { phase: 'idle', phone: '', secondsLeft: 0, timerId: null };

// implements: AC4
function _validatePhone(raw) {
  let n = raw.replace(/[\s\-]/g, '');
  if (n.startsWith('0')) n = n.slice(1);
  if (n.length === 10) n = '+91' + n;
  else if (n.startsWith('91') && n.length === 12) n = '+' + n;
  const valid = /^\+91[6-9]\d{9}$/.test(n);
  return {
    valid,
    normalised: n,
    error: valid ? '' : 'Enter a valid 10-digit Indian mobile number.',
  };
}

// implements: AC14
function _formatCountdown(s) {
  const m = Math.floor(s / 60);
  const sec = String(s % 60).padStart(2, '0');
  return `${m}:${sec}`;
}

// implements: AC6, AC14
function _startCountdown(seconds) {
  _stopCountdown();
  _otpState.secondsLeft = seconds;
  const countdownEl = document.getElementById('otpCountdown');
  const resendBtn   = document.getElementById('resendBtn');
  const verifyBtn   = document.getElementById('verifyOtpBtn');
  if (countdownEl) {
    countdownEl.classList.remove('expired');
    countdownEl.textContent = _formatCountdown(_otpState.secondsLeft);
  }
  _otpState.timerId = setInterval(() => {
    _otpState.secondsLeft -= 1;
    const el = document.getElementById('otpCountdown');
    if (_otpState.secondsLeft <= 0) {
      clearInterval(_otpState.timerId);
      _otpState.timerId = null;
      if (el) {
        el.textContent = 'OTP expired'; // implements: AC14
        el.classList.add('expired');
      }
      const rb = document.getElementById('resendBtn');
      const vb = document.getElementById('verifyOtpBtn');
      if (rb) rb.disabled = false; // implements: AC14
      if (vb) vb.disabled = true;  // implements: AC14
    } else {
      if (el) el.textContent = _formatCountdown(_otpState.secondsLeft);
    }
  }, 1000);
}

// implements: AC17
function _stopCountdown() {
  if (_otpState.timerId !== null) {
    clearInterval(_otpState.timerId);
    _otpState.timerId = null;
  }
}

function _setPhoneError(msg) {
  const el = document.getElementById('phoneError');
  if (!el) return;
  el.textContent = msg;
  el.style.display = msg ? '' : 'none';
}

function _setOtpError(msg) {
  const el = document.getElementById('otpError');
  if (!el) return;
  el.textContent = msg;
  el.style.display = msg ? '' : 'none';
}

// implements: AC5, AC9
function _renderOtpPhase(phase) {
  _otpState.phase = phase;
  const sendBtn   = document.getElementById('sendOtpBtn');
  const verifyBtn = document.getElementById('verifyOtpBtn');
  const resendBtn = document.getElementById('resendBtn');

  if (phase === 'idle') {
    if (sendBtn) { sendBtn.textContent = 'Send OTP'; sendBtn.disabled = false; }
  } else if (phase === 'sending') {
    if (sendBtn) { sendBtn.textContent = 'Sending…'; sendBtn.disabled = true; } // implements: AC5
  } else if (phase === 'otp_sent') {
    // Show OTP step, hide phone step
    const stepPhone = document.getElementById('otpStepPhone');
    const stepCode  = document.getElementById('otpStepCode');
    if (stepPhone) stepPhone.style.display = 'none';
    if (stepCode)  stepCode.style.display  = '';
    if (verifyBtn) { verifyBtn.textContent = 'Verify & Continue'; verifyBtn.disabled = false; }
    if (resendBtn) resendBtn.disabled = true;
  } else if (phase === 'verifying') {
    if (verifyBtn) { verifyBtn.textContent = 'Verifying…'; verifyBtn.disabled = true; } // implements: AC9
  }
}

// implements: AC5, AC6, AC16
async function _submitSendOtp(isResend = false) {
  _setPhoneError('');
  const phoneInput = document.getElementById('phoneInput');
  const raw = phoneInput ? phoneInput.value : '';
  const { valid, normalised, error } = _validatePhone(raw);
  if (!valid) {
    _setPhoneError(error); // implements: AC4
    return;
  }
  _otpState.phone = normalised;
  _renderOtpPhase('sending'); // implements: AC5
  try {
    const data = await sendOtp(_otpState.phone);
    _otpState.phase = 'otp_sent';
    const hint = document.getElementById('otpSentHint');
    if (hint) hint.textContent = `OTP sent to ${_otpState.phone}`; // implements: AC6
    _startCountdown(data.expiresIn || 300); // implements: AC6
    _renderOtpPhase('otp_sent');
    if (isResend) showToast('success', 'OTP Resent', `A new OTP has been sent to ${_otpState.phone}`); // implements: AC15
  } catch (err) {
    let msg = err.message || 'Failed to send OTP. Please try again.';
    if (err.status === 429 || err.code === 'RATE_LIMIT') {
      msg = 'Too many requests. Please wait before trying again.'; // implements: AC16
    }
    _setPhoneError(msg);
    _renderOtpPhase('idle');
  }
}

// implements: AC8, AC9, AC10, AC11, AC12, AC13
async function _submitVerifyOtp() {
  _setOtpError('');
  const otpInput = document.getElementById('otpInput');
  const otp = otpInput ? otpInput.value.trim() : '';
  if (!/^\d{6}$/.test(otp)) {
    _setOtpError('Enter the 6-digit OTP sent to your phone.'); // implements: AC7
    return;
  }
  _renderOtpPhase('verifying'); // implements: AC9
  try {
    const data = await verifyOtp(_otpState.phone, otp);
    _stopCountdown();                                          // implements: AC17
    store.setUser(data.user, data.accessToken);               // implements: AC10
    window.location.hash = '/';                               // implements: AC10
  } catch (err) {
    let msg;
    const code = err.code || '';
    if (code === 'INVALID_OTP') {
      msg = 'Incorrect OTP. Please check and try again.';     // implements: AC11
      if (otpInput) { otpInput.value = ''; otpInput.focus(); }
    } else if (code === 'OTP_EXPIRED') {
      msg = 'OTP has expired. Please request a new one.';     // implements: AC12
      const rb = document.getElementById('resendBtn');
      if (rb) rb.disabled = false;
    } else if (code === 'TOO_MANY_ATTEMPTS') {
      msg = 'Too many incorrect attempts. Request a new OTP.'; // implements: AC13
      const rb = document.getElementById('resendBtn');
      if (rb) rb.disabled = false;
    } else {
      msg = err.message || 'Verification failed. Please try again.';
    }
    _setOtpError(msg);
    _renderOtpPhase('otp_sent');
  }
}

function _bindDevTestLogin() {
  if (!IS_DEV) return;
  const btn = document.getElementById('devTestLoginBtn');
  if (!btn) return;
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    btn.textContent = 'Logging in…';
    try {
      const data = await testLogin();
      store.setUser(data.user, data.accessToken);
      window.location.hash = '/';
    } catch (err) {
      showToast('error', 'Dev login failed', err.message || 'Make sure DEV_MODE=true on the server.');
      btn.disabled = false;
      btn.textContent = '🧪 Dev Test Login';
    }
  });
}

// implements: AC1, AC2
function _bindTabs() {
  const tabGoogle = document.getElementById('tabGoogle');
  const tabPhone  = document.getElementById('tabPhone');
  const panelGoogle = document.getElementById('panelGoogle');
  const panelPhone  = document.getElementById('panelPhone');
  if (!tabGoogle || !tabPhone) return;

  tabGoogle.addEventListener('click', () => {
    tabGoogle.classList.add('active');
    tabPhone.classList.remove('active');
    tabGoogle.setAttribute('aria-selected', 'true');
    tabPhone.setAttribute('aria-selected', 'false');
    if (panelGoogle) panelGoogle.classList.add('active');
    if (panelPhone)  panelPhone.classList.remove('active');
  });

  tabPhone.addEventListener('click', () => {
    tabPhone.classList.add('active');
    tabGoogle.classList.remove('active');
    tabPhone.setAttribute('aria-selected', 'true');
    tabGoogle.setAttribute('aria-selected', 'false');
    if (panelPhone)  panelPhone.classList.add('active');
    if (panelGoogle) panelGoogle.classList.remove('active');
  });
}

// implements: AC3–AC16
function _bindOtp() {
  const sendOtpBtn  = document.getElementById('sendOtpBtn');
  const phoneInput  = document.getElementById('phoneInput');
  const otpInput    = document.getElementById('otpInput');
  const verifyBtn   = document.getElementById('verifyOtpBtn');
  const resendBtn   = document.getElementById('resendBtn');

  sendOtpBtn?.addEventListener('click', () => _submitSendOtp(false)); // implements: AC5

  phoneInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') _submitSendOtp(false);
  });

  // implements: AC7, AC8
  otpInput?.addEventListener('input', () => {
    otpInput.value = otpInput.value.replace(/\D/g, '').slice(0, 6);
    if (otpInput.value.length === 6) {
      _submitVerifyOtp(); // implements: AC8
    }
  });

  verifyBtn?.addEventListener('click', () => _submitVerifyOtp()); // implements: AC8

  resendBtn?.addEventListener('click', () => {
    // Show phone step again so user can confirm number, then send
    const stepPhone = document.getElementById('otpStepPhone');
    const stepCode  = document.getElementById('otpStepCode');
    if (stepPhone) stepPhone.style.display = '';
    if (stepCode)  stepCode.style.display  = 'none';
    _renderOtpPhase('idle');
    _stopCountdown();
    _submitSendOtp(true); // implements: AC15
  });
}

// ── Template ─────────────────────────────────────────────────────

// implements: AC1
const LOGIN_TEMPLATE = `
<div style="min-height:calc(100vh - var(--header-h) - var(--bottom-nav-h));display:flex;align-items:center;justify-content:center;padding:var(--sp-8) var(--sp-4);background:var(--clr-bg)">
  <div style="width:100%;max-width:400px">

    <!-- Logo & tagline -->
    <div style="text-align:center;margin-bottom:var(--sp-8)">
      <a href="#/" style="display:inline-block;margin-bottom:var(--sp-4)">
        <img src="assets/images/saroori-logo.png" alt="Saroori" height="60" />
      </a>
      <p style="color:var(--clr-text-m);font-size:var(--ts-sm);font-style:italic">"From Our Home to Your Home"</p>
    </div>

    <!-- Card -->
    <div style="background:var(--clr-surface);border-radius:var(--r-xl);padding:var(--sp-8);box-shadow:var(--shadow-md);border:1px solid var(--clr-border)">
      <h1 style="text-align:center;font-size:var(--ts-xl);margin-bottom:var(--sp-2)">Sign In</h1>
      <p style="text-align:center;color:var(--clr-text-m);font-size:var(--ts-sm);margin-bottom:var(--sp-6)">Sign in to track orders and manage your cart.</p>

      <!-- Tab bar — implements: AC1, AC2 -->
      <div class="tab-bar" role="tablist">
        <button class="tab-btn active" id="tabGoogle" role="tab" aria-selected="true" aria-controls="panelGoogle">Continue with Google</button>
        <button class="tab-btn" id="tabPhone" role="tab" aria-selected="false" aria-controls="panelPhone">Phone Number</button>
      </div>

      <!-- Panel: Google — implements: AC19 -->
      <div class="tab-panel active" id="panelGoogle" role="tabpanel" aria-labelledby="tabGoogle">
        <button class="btn-google" id="googleLoginBtn" style="width:100%;display:flex;align-items:center;justify-content:center;gap:var(--sp-3)">
          <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
            <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9 3.2l6.7-6.7C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.6 13.3l7.8 6.1C12.3 13.2 17.7 9.5 24 9.5z"/>
            <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v8.5h12.7c-.5 2.9-2.2 5.3-4.7 6.9l7.3 5.7c4.3-4 6.8-9.9 6.8-16.6z"/>
            <path fill="#FBBC05" d="M10.4 28.6C9.8 26.9 9.5 25 9.5 23s.3-3.9.9-5.6L2.6 11.3C1 14.5 0 18.1 0 23s1 8.5 2.6 11.7l7.8-6.1z"/>
            <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.3-5.7c-2.2 1.5-5 2.3-8.6 2.3-6.3 0-11.7-3.7-13.6-9.1l-7.8 6.1C6.6 42.6 14.6 48 24 48z"/>
          </svg>
          Continue with Google
        </button>
      </div>

      <!-- Panel: Phone OTP — implements: AC3–AC16 -->
      <div class="tab-panel" id="panelPhone" role="tabpanel" aria-labelledby="tabPhone">

        <!-- Step A: Phone entry — implements: AC3, AC4, AC5 -->
        <div id="otpStepPhone">
          <div class="form-group">
            <label class="form-label" for="phoneInput">Mobile Number</label>
            <div class="otp-phone-wrapper">
              <span class="otp-country-code">+91</span>
              <input class="form-input" id="phoneInput" type="tel" inputmode="numeric"
                     placeholder="9876543210" autocomplete="tel-national" maxlength="10" />
            </div>
            <p class="form-error" id="phoneError" style="display:none"></p>
          </div>
          <button class="btn btn-primary" id="sendOtpBtn" style="width:100%">Send OTP</button>
        </div>

        <!-- Step B: OTP entry (hidden initially) — implements: AC6, AC7, AC8, AC14 -->
        <div id="otpStepCode" style="display:none">
          <p class="otp-sent-hint" id="otpSentHint">OTP sent to +91XXXXXXXXXX</p>
          <div class="form-group">
            <label class="form-label" for="otpInput">Enter 6-digit OTP</label>
            <input class="form-input otp-code-input" id="otpInput" type="text"
                   inputmode="numeric" maxlength="6" autocomplete="one-time-code"
                   pattern="[0-9]{6}" placeholder="——————" />
            <p class="form-error" id="otpError" style="display:none"></p>
          </div>
          <div class="otp-timer-row">
            <span id="otpCountdown"></span>
            <button class="btn-ghost btn-sm" id="resendBtn" disabled>Resend OTP</button>
          </div>
          <button class="btn btn-primary" id="verifyOtpBtn" style="width:100%">Verify &amp; Continue</button>
        </div>

      </div><!-- /panelPhone -->

      <!-- Divider — implements: AC1 (Guest always visible) -->
      <div style="display:flex;align-items:center;gap:var(--sp-3);margin:var(--sp-5) 0;color:var(--clr-text-m);font-size:var(--ts-xs)">
        <div style="flex:1;height:1px;background:var(--clr-border)"></div>
        <span>or</span>
        <div style="flex:1;height:1px;background:var(--clr-border)"></div>
      </div>

      <!-- Guest button — implements: AC1 -->
      <button class="btn btn-secondary" id="guestBtn" style="width:100%">
        Continue as Guest →
      </button>

      ${IS_DEV ? `
      <button id="devTestLoginBtn" style="width:100%;margin-top:var(--sp-3);padding:10px;background:#fff3cd;border:1.5px dashed #856404;border-radius:var(--r-md);color:#856404;font-size:var(--ts-sm);font-weight:600;cursor:pointer;">
        🧪 Dev Test Login
      </button>
      ` : ''}

      <p style="text-align:center;font-size:var(--ts-xs);color:var(--clr-text-m);margin-top:var(--sp-5);line-height:1.6">
        By continuing, you agree to our
        <a href="#/terms" style="color:var(--clr-primary)">Terms of Service</a>
        and <a href="#/privacy" style="color:var(--clr-primary)">Privacy Policy</a>.
      </p>
    </div>
  </div>
</div>
`;

export default page;
