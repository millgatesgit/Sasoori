/**
 * SASOORI — Admin API
 * All endpoints require ADMIN role.
 */
import api from './apiClient.js';

// ── Dashboard ─────────────────────────────────────────────────────────────

export function getDashboard() {
  return api.get('/admin/dashboard');
}

// ── Orders ────────────────────────────────────────────────────────────────

export function adminGetOrders({ status, page = 1, size = 20 } = {}) {
  const qs = new URLSearchParams({ page, size });
  if (status) qs.set('status', status);
  return api.get(`/admin/orders?${qs}`);
}

export function adminUpdateOrderStatus(id, status) {
  return api.put(`/admin/orders/${id}/status`, { status });
}

// ── Products ──────────────────────────────────────────────────────────────

export function adminCreateProduct(data) {
  return api.post('/admin/products', data);
}

export function adminUpdateProduct(id, data) {
  return api.put(`/admin/products/${id}`, data);
}

export function adminDeleteProduct(id) {
  return api.delete(`/admin/products/${id}`);
}

// ── Users ─────────────────────────────────────────────────────────────────

export function adminGetUsers({ page = 1, size = 20 } = {}) {
  return api.get(`/admin/users?page=${page}&size=${size}`);
}

export function adminSetUserActive(id, active) {
  return api.put(`/admin/users/${id}/active`, { active });
}

export function adminSetUserRole(id, role) {
  return api.put(`/admin/users/${id}/role`, { role });
}

// ── Shipments ────────────────────────────────────────────────────────
export function adminGetShipments({ page = 1, size = 20, status = '' } = {}) {
  const q = new URLSearchParams({ page, size });
  if (status) q.set('status', status);
  return api.get(`/admin/shipments?${q}`);
}

export function adminCreateShipment(orderId) {
  return api.post(`/admin/shipments/${orderId}`);
}

export function adminTrackShipment(orderId) {
  return api.get(`/admin/shipments/${orderId}/track`);
}

// ── Invoices ─────────────────────────────────────────────────────────
export function adminGetInvoices({ page = 1, size = 20 } = {}) {
  const q = new URLSearchParams({ page, size });
  return api.get(`/admin/invoices?${q}`);
}

export function adminGetOrderForInvoice(orderId) {
  return api.get(`/admin/orders/${orderId}`);
}
