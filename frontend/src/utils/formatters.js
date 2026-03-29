/**
 * SASOORI — Formatters
 * Pure utility functions, no side effects.
 */

/**
 * Format paise to INR string
 * @param {number} paise  e.g. 19900
 * @returns {string}  e.g. "₹199"
 */
export function formatPrice(paise) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(paise / 100);
}

/**
 * Format ISO date string to readable date
 * @param {string} iso
 * @returns {string}  e.g. "27 Mar 2025"
 */
export function formatDate(iso) {
  return new Intl.DateTimeFormat('en-IN', {
    day:   '2-digit',
    month: 'short',
    year:  'numeric',
  }).format(new Date(iso));
}

/**
 * Format ISO date with time
 * @param {string} iso
 * @returns {string}  e.g. "27 Mar 2025, 10:30 AM"
 */
export function formatDateTime(iso) {
  return new Intl.DateTimeFormat('en-IN', {
    day:    '2-digit',
    month:  'short',
    year:   'numeric',
    hour:   '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));
}

/**
 * Format order status to human-readable label
 */
export function formatOrderStatus(status) {
  const map = {
    PENDING:           'Pending',
    PAYMENT_INITIATED: 'Payment Pending',
    PAID:              'Paid',
    PROCESSING:        'Processing',
    SHIPPED:           'Shipped',
    DELIVERED:         'Delivered',
    CANCELLED:         'Cancelled',
    REFUND_INITIATED:  'Refund Initiated',
    REFUNDED:          'Refunded',
  };
  return map[status] || status;
}

/**
 * Get badge CSS class for order status
 */
export function orderStatusBadgeClass(status) {
  const map = {
    PENDING:           'badge-pending',
    PAYMENT_INITIATED: 'badge-pending',
    PAID:              'badge-paid',
    PROCESSING:        'badge-processing',
    SHIPPED:           'badge-shipped',
    DELIVERED:         'badge-delivered',
    CANCELLED:         'badge-cancelled',
    REFUND_INITIATED:  'badge-pending',
    REFUNDED:          'badge-cancelled',
  };
  return map[status] || 'badge-pending';
}

/**
 * Convert product name to URL slug
 */
export function slugify(text) {
  return text.toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-');
}

/**
 * Truncate text at word boundary
 */
export function truncate(text, maxLen = 100) {
  if (!text || text.length <= maxLen) return text;
  return text.slice(0, text.lastIndexOf(' ', maxLen)) + '…';
}

/**
 * Discount percentage
 */
export function discountPercent(mrpPaise, pricePaise) {
  if (!mrpPaise || mrpPaise <= pricePaise) return 0;
  return Math.round((1 - pricePaise / mrpPaise) * 100);
}
