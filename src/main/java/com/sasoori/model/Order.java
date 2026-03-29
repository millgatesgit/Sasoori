package com.sasoori.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Represents a row from the {@code orders} table.
 * The {@code items} field is transient — populated via JOIN query, not a DB column.
 */
public class Order {

    private String         id;
    private String         userId;
    private String         status;
    private int            subtotalPaise;
    private int            shippingPaise;
    private int            discountPaise;
    private int            totalPaise;
    /** Raw JSON snapshot of the shipping address at time of order. */
    private String         shippingAddress;
    private String         razorpayOrderId;
    private String         notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Transient — not stored in orders table; filled by DAO from order_items. */
    private transient List<OrderItem> items;

    public Order() {}

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getId()                              { return id; }
    public void   setId(String id)                     { this.id = id; }

    public String getUserId()                          { return userId; }
    public void   setUserId(String userId)             { this.userId = userId; }

    public String getStatus()                          { return status; }
    public void   setStatus(String status)             { this.status = status; }

    public int    getSubtotalPaise()                   { return subtotalPaise; }
    public void   setSubtotalPaise(int subtotalPaise)  { this.subtotalPaise = subtotalPaise; }

    public int    getShippingPaise()                   { return shippingPaise; }
    public void   setShippingPaise(int shippingPaise)  { this.shippingPaise = shippingPaise; }

    public int    getDiscountPaise()                   { return discountPaise; }
    public void   setDiscountPaise(int discountPaise)  { this.discountPaise = discountPaise; }

    public int    getTotalPaise()                      { return totalPaise; }
    public void   setTotalPaise(int totalPaise)        { this.totalPaise = totalPaise; }

    public String getShippingAddress()                         { return shippingAddress; }
    public void   setShippingAddress(String shippingAddress)   { this.shippingAddress = shippingAddress; }

    public String getRazorpayOrderId()                         { return razorpayOrderId; }
    public void   setRazorpayOrderId(String razorpayOrderId)   { this.razorpayOrderId = razorpayOrderId; }

    public String getNotes()                           { return notes; }
    public void   setNotes(String notes)               { this.notes = notes; }

    public OffsetDateTime getCreatedAt()               { return createdAt; }
    public void           setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt()               { return updatedAt; }
    public void           setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<OrderItem> getItems()                  { return items; }
    public void            setItems(List<OrderItem> items) { this.items = items; }
}
