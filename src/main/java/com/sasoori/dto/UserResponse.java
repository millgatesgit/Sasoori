package com.sasoori.dto;

import com.sasoori.model.User;

/**
 * Safe user representation returned to the frontend (no sensitive fields).
 */
public class UserResponse {
    public final String id;
    public final String name;
    public final String email;
    public final String phone;
    public final String picture;
    public final String role;

    public UserResponse(User u) {
        this.id      = u.getId();
        this.name    = u.getName();
        this.email   = u.getEmail();
        this.phone   = u.getPhone();
        this.picture = u.getPictureUrl();
        this.role    = u.getRole();
    }
}
