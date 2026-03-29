/**
 * SASOORI — Order, Address & Payment API
 */
import api from './apiClient.js';

// ── Config ───────────────────────────────────────────────────────────────

let _config = null;

export async function getConfig() {
  if (_config) return _config;
  _config = await api.get('/config');
  return _config;
}

// ── Addresses ────────────────────────────────────────────────────────────

export function getAddresses() {
  return api.get('/addresses');
}

export function createAddress(data) {
  return api.post('/addresses', data);
}

export function updateAddress(id, data) {
  return api.put(`/addresses/${id}`, data);
}

export function deleteAddress(id) {
  return api.delete(`/addresses/${id}`);
}

export function setDefaultAddress(id) {
  return api.post(`/addresses/${id}/default`);
}

// ── Orders ───────────────────────────────────────────────────────────────

export function placeOrder(addressId, notes = '') {
  return api.post('/orders', { addressId, notes });
}

export function getOrders(page = 1, size = 10) {
  return api.get(`/orders?page=${page}&size=${size}`);
}

export function getOrder(id) {
  return api.get(`/orders/${id}`);
}

export function cancelOrder(id) {
  return api.post(`/orders/${id}/cancel`);
}

// ── Payments ─────────────────────────────────────────────────────────────

export function verifyPayment(orderId, razorpayPaymentId, razorpaySignature) {
  return api.post('/payments/verify', { orderId, razorpayPaymentId, razorpaySignature });
}
