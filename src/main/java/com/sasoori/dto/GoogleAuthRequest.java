package com.sasoori.dto;

/**
 * Request body for POST /api/v1/auth/google
 * Carries the PKCE authorization code exchange payload.
 */
public class GoogleAuthRequest {
    public String code;
    public String codeVerifier;
    public String state;
}
