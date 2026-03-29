package com.sasoori.model;

/**
 * Represents a row from the {@code order_items} table.
 */
public class OrderItem {

    private int    id;
    private String orderId;
    private String productId;
    private String productName;
    private String productSku;
    private int    quantity;
    private int    unitPricePaise;
    private int    totalPaise;

    public OrderItem() {}

    // ── Getters & Setters ─────────────────────────────────────────────────

    public int    getId()                              { return id; }
    public void   setId(int id)                        { this.id = id; }

    public String getOrderId()                         { return orderId; }
    public void   setOrderId(String orderId)           { this.orderId = orderId; }

    public String getProductId()                       { return productId; }
    public void   setProductId(String productId)       { this.productId = productId; }

    public String getProductName()                     { return productName; }
    public void   setProductName(String productName)   { this.productName = productName; }

    public String getProductSku()                      { return productSku; }
    public void   setProductSku(String productSku)     { this.productSku = productSku; }

    public int    getQuantity()                        { return quantity; }
    public void   setQuantity(int quantity)            { this.quantity = quantity; }

    public int    getUnitPricePaise()                  { return unitPricePaise; }
    public void   setUnitPricePaise(int unitPricePaise){ this.unitPricePaise = unitPricePaise; }

    public int    getTotalPaise()                      { return totalPaise; }
    public void   setTotalPaise(int totalPaise)        { this.totalPaise = totalPaise; }
}
