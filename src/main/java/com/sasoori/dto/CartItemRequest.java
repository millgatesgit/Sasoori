package com.sasoori.dto;

/**
 * Request body for adding or updating a cart item.
 * Used by POST /api/v1/cart/items and PUT /api/v1/cart/items/{pid}.
 */
public class CartItemRequest {

    public String productId;
    public int    quantity;
}
