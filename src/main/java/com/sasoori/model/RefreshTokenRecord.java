package com.sasoori.model;

import java.time.OffsetDateTime;

/**
 * Mirrors the {@code refresh_tokens} table row.
 */
public class RefreshTokenRecord {

    private String         id;
    private String         userId;
    private String         tokenHash;   // SHA-256 of raw token
    private boolean        isRevoked;
    private String         userAgent;
    private String         ipAddress;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;

    public RefreshTokenRecord() {}

    public String getId()                 { return id; }
    public void   setId(String id)        { this.id = id; }

    public String getUserId()                  { return userId; }
    public void   setUserId(String userId)     { this.userId = userId; }

    public String getTokenHash()                     { return tokenHash; }
    public void   setTokenHash(String tokenHash)     { this.tokenHash = tokenHash; }

    public boolean isRevoked()                   { return isRevoked; }
    public void    setRevoked(boolean revoked)   { isRevoked = revoked; }

    public String getUserAgent()                       { return userAgent; }
    public void   setUserAgent(String userAgent)       { this.userAgent = userAgent; }

    public String getIpAddress()                       { return ipAddress; }
    public void   setIpAddress(String ipAddress)       { this.ipAddress = ipAddress; }

    public OffsetDateTime getExpiresAt()                      { return expiresAt; }
    public void           setExpiresAt(OffsetDateTime v)      { this.expiresAt = v; }

    public OffsetDateTime getCreatedAt()                      { return createdAt; }
    public void           setCreatedAt(OffsetDateTime v)      { this.createdAt = v; }
}
