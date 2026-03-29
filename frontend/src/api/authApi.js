import api from './apiClient.js';

export function logout() {
  return api.post('/auth/logout');
}

export function getMe() {
  return api.get('/auth/me');
}

// implements: AC20
export function sendOtp(phone) {
  return api.post('/auth/otp/send', { phone });
}

// implements: AC20
export function verifyOtp(phone, otp) {
  return api.post('/auth/otp/verify', { phone, otp });
}

// Dev-only: bypasses OAuth/OTP; only works when DEV_MODE=true on the server
export function testLogin() {
  return api.post('/auth/test-login', {});
}

export function registerWithPassword(name, email, password) {
  return api.post('/auth/register', { name, email, password });
}

export function loginWithPassword(email, password) {
  return api.post('/auth/login', { email, password });
}
