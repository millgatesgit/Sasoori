import api from './apiClient.js';

/**
 * Get product list with filters
 * @param {{ page, size, category, search, sort, featured, tag }} params
 */
export function getProducts(params = {}) {
  const qs = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([,v]) => v !== undefined && v !== null))
  );
  return api.get(`/products${qs.toString() ? '?' + qs : ''}`);
}

/** Get single product by slug */
export function getProductBySlug(slug) {
  return api.get(`/products/slug/${encodeURIComponent(slug)}`);
}

/** Get single product by ID */
export function getProductById(id) {
  return api.get(`/products/${id}`);
}

/** Get all categories */
export function getCategories() {
  return api.get('/categories');
}
