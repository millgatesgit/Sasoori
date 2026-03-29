import api from './apiClient.js';
import store from '../store.js';

export function getCart() {
  return api.get('/cart');
}

export async function addToCart(productId, quantity = 1) {
  const data = await api.post('/cart/items', { productId, quantity });
  if (data?.items) store.setCart(data.items);
  return data;
}

export async function updateCartItem(productId, quantity) {
  const data = await api.put(`/cart/items/${productId}`, { quantity });
  if (data?.items) store.setCart(data.items);
  return data;
}

export async function removeCartItem(productId) {
  const data = await api.delete(`/cart/items/${productId}`);
  if (data?.items) store.setCart(data.items);
  return data;
}

export async function clearCart() {
  const data = await api.delete('/cart');
  store.setCart([]);
  return data;
}
