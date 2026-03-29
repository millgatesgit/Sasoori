package com.sasoori.dto;

import com.sasoori.model.CartItem;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON response shape for GET /api/v1/cart.
 * Includes computed totals and a flat list of {@link CartItemDto}.
 */
public class CartResponse {

    public final List<CartItemDto> items;
    public final int               itemCount;
    public final int               subtotalPaise;

    public CartResponse(List<CartItem> cartItems) {
        this.items = cartItems.stream()
                .map(CartItemDto::new)
                .collect(Collectors.toList());

        this.itemCount     = cartItems.stream().mapToInt(CartItem::getQuantity).sum();
        this.subtotalPaise = cartItems.stream()
                .mapToInt(ci -> ci.getQuantity() * ci.getPricePaise())
                .sum();
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────

    public static class CartItemDto {

        public final String productId;
        public final String productName;
        public final String productSlug;
        public final String productSku;
        public final int    weightGrams;
        public final int    pricePaise;
        public final int    mrpPaise;
        public final String imageUrl;
        public final int    quantity;
        public final int    lineTotalPaise;

        CartItemDto(CartItem ci) {
            this.productId      = ci.getProductId();
            this.productName    = ci.getProductName();
            this.productSlug    = ci.getProductSlug();
            this.productSku     = ci.getProductSku();
            this.weightGrams    = ci.getWeightGrams();
            this.pricePaise     = ci.getPricePaise();
            this.mrpPaise       = ci.getMrpPaise();
            this.imageUrl       = ci.getImageUrl();
            this.quantity       = ci.getQuantity();
            this.lineTotalPaise = ci.getQuantity() * ci.getPricePaise();
        }
    }
}
