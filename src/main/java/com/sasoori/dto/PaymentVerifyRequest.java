package com.sasoori.dto;

/** Request body for POST /api/v1/payments/verify */
public class PaymentVerifyRequest {
    public String orderId;
    public String razorpayPaymentId;
    public String razorpaySignature;
}
