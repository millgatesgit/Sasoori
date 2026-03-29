package com.sasoori.model;

import java.time.OffsetDateTime;

/**
 * Mirrors the {@code otp_verifications} table row.
 */
public class OtpVerification {

    private long           id;
    private String         phone;
    private String         otpHash;
    private int            attempts;
    private boolean        isUsed;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;

    public OtpVerification() {}

    public long   getId()                { return id; }
    public void   setId(long id)         { this.id = id; }

    public String getPhone()             { return phone; }
    public void   setPhone(String v)     { this.phone = v; }

    public String getOtpHash()           { return otpHash; }
    public void   setOtpHash(String v)   { this.otpHash = v; }

    public int  getAttempts()            { return attempts; }
    public void setAttempts(int v)       { this.attempts = v; }

    public boolean isUsed()              { return isUsed; }
    public void    setUsed(boolean v)    { this.isUsed = v; }

    public OffsetDateTime getExpiresAt()            { return expiresAt; }
    public void           setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }

    public OffsetDateTime getCreatedAt()            { return createdAt; }
    public void           setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
