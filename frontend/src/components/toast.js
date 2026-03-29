/**
 * SASOORI — Toast Notifications
 * showToast('success' | 'error' | 'warning' | 'info', title, message, duration?)
 */

const ICONS = {
  success: '✅',
  error:   '❌',
  warning: '⚠️',
  info:    'ℹ️',
};

const DEFAULT_DURATION = 3500; // ms

/**
 * Show a toast notification.
 * @param {'success'|'error'|'warning'|'info'} type
 * @param {string} title
 * @param {string} [message]
 * @param {number} [duration]
 */
export function showToast(type, title, message = '', duration = DEFAULT_DURATION) {
  const container = document.getElementById('toastContainer');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.setAttribute('role', 'alert');
  toast.innerHTML = `
    <span class="toast-icon" aria-hidden="true">${ICONS[type] || 'ℹ️'}</span>
    <div class="toast-body">
      <div class="toast-title">${_esc(title)}</div>
      ${message ? `<div class="toast-message">${_esc(message)}</div>` : ''}
    </div>
    <button class="toast-close" aria-label="Dismiss">✕</button>
  `;

  container.appendChild(toast);

  let dismissed = false;
  function dismiss() {
    if (dismissed) return;
    dismissed = true;
    clearTimeout(autoTimer);
    toast.classList.add('removing');
    // Remove after animation; timeout is a guaranteed fallback if animationend doesn't fire
    const remove = () => toast.remove();
    toast.addEventListener('animationend', remove, { once: true });
    setTimeout(remove, 350);
  }

  // Dismiss on close button
  toast.querySelector('.toast-close').addEventListener('click', dismiss);

  // Auto dismiss with hover pause
  let autoTimer = setTimeout(dismiss, duration);
  toast.addEventListener('mouseenter', () => clearTimeout(autoTimer));
  toast.addEventListener('mouseleave', () => { autoTimer = setTimeout(dismiss, 1000); });
}

function _esc(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
