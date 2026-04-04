import api from './apiClient.js';

export function getProfile() {
  return api.get('/user/profile');
}

export function updateProfile(data) {
  return api.put('/user/profile', data);
}
