package com.sasoori.dto;

/**
 * Successful auth response body.
 * The refresh token is NOT included here — it is set as an HttpOnly cookie.
 */
public class AuthResponse {
    public final String       accessToken;
    public final UserResponse user;

    public AuthResponse(String accessToken, UserResponse user) {
        this.accessToken = accessToken;
        this.user        = user;
    }
}
