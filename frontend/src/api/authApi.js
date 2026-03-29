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
