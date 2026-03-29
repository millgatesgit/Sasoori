package com.sasoori.dto;

/** Request body for POST /api/v1/auth/otp/verify */
public class OtpVerifyRequest {
    public String phone;
    public String otp;
}
