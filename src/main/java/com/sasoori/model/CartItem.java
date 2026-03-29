package com.sasoori.model;

import java.time.OffsetDateTime;

/**
 * Represents a row from {@code cart_items} joined with {@code products}.
 * Denormalized product fields are populated by {@link com.sasoori.dao.CartDao#getCartItems}.
 */
public class CartItem {

    private String         cartId;
    private String         productId;
    private int            quantity;
    private OffsetDateTime addedAt;

    // Denormalized product fields
    private String productName;
    private String productSlug;
    private String productSku;
    private int    weightGrams;
    private int    pricePaise;
    private int    mrpPaise;
    private String imageUrl;

    public CartItem() {}

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getCartId()                        { return cartId; }
    public void   setCartId(String cartId)           { this.cartId = cartId; }

    public String getProductId()                     { return productId; }
    public void   setProductId(String productId)     { this.productId = productId; }

    public int  getQuantity()                        { return quantity; }
    public void setQuantity(int quantity)            { this.quantity = quantity; }

    public OffsetDateTime getAddedAt()               { return addedAt; }
    public void           setAddedAt(OffsetDateTime addedAt) { this.addedAt = addedAt; }

    public String getProductName()                   { return productName; }
    public void   setProductName(String productName) { this.productName = productName; }

    public String getProductSlug()                   { return productSlug; }
    public void   setProductSlug(String productSlug) { this.productSlug = productSlug; }

    public String getProductSku()                    { return productSku; }
    public void   setProductSku(String productSku)   { this.productSku = productSku; }

    public int  getWeightGrams()                     { return weightGrams; }
    public void setWeightGrams(int weightGrams)      { this.weightGrams = weightGrams; }

    public int  getPricePaise()                      { return pricePaise; }
    public void setPricePaise(int pricePaise)        { this.pricePaise = pricePaise; }

    public int  getMrpPaise()                        { return mrpPaise; }
    public void setMrpPaise(int mrpPaise)            { this.mrpPaise = mrpPaise; }

    public String getImageUrl()                      { return imageUrl; }
    public void   setImageUrl(String imageUrl)       { this.imageUrl = imageUrl; }
}
