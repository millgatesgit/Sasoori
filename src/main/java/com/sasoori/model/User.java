package com.sasoori.model;

import java.time.OffsetDateTime;

/**
 * Mirrors the {@code users} table row.
 */
public class User {

    private String         id;          // UUID
    private String         googleSub;
    private String         email;
    private String         phone;
    private String         name;
    private String         pictureUrl;
    private String         role;        // CUSTOMER | ADMIN
    private boolean        isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public User() {}

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()                 { return id; }
    public void   setId(String id)        { this.id = id; }

    public String getGoogleSub()                   { return googleSub; }
    public void   setGoogleSub(String googleSub)   { this.googleSub = googleSub; }

    public String getEmail()              { return email; }
    public void   setEmail(String email)  { this.email = email; }

    public String getPhone()              { return phone; }
    public void   setPhone(String phone)  { this.phone = phone; }

    public String getName()               { return name; }
    public void   setName(String name)    { this.name = name; }

    public String getPictureUrl()                       { return pictureUrl; }
    public void   setPictureUrl(String pictureUrl)      { this.pictureUrl = pictureUrl; }

    public String getRole()               { return role; }
    public void   setRole(String role)    { this.role = role; }

    public boolean isActive()                  { return isActive; }
    public void    setActive(boolean active)   { isActive = active; }

    public OffsetDateTime getCreatedAt()                      { return createdAt; }
    public void           setCreatedAt(OffsetDateTime v)      { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt()                      { return updatedAt; }
    public void           setUpdatedAt(OffsetDateTime v)      { this.updatedAt = v; }
}
