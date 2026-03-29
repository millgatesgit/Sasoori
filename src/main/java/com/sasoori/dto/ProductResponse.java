package com.sasoori.dto;

import com.sasoori.model.Product;

import java.util.List;

/**
 * Thin DTO for serialising a {@link Product} to JSON API responses.
 */
public class ProductResponse {

    public final String       id;
    public final String       name;
    public final String       slug;
    public final String       sku;
    public final String       description;
    public final String       ingredients;
    public final int          weightGrams;
    public final int          pricePaise;
    public final int          mrpPaise;
    public final int          stockQty;
    public final List<String> images;
    public final List<String> tags;
    public final boolean      isActive;
    public final boolean      isFeatured;
    public final int          categoryId;
    public final String       categoryName;
    public final String       categorySlug;

    public ProductResponse(Product p) {
        this.id           = p.getId();
        this.name         = p.getName();
        this.slug         = p.getSlug();
        this.sku          = p.getSku();
        this.description  = p.getDescription();
        this.ingredients  = p.getIngredients();
        this.weightGrams  = p.getWeightGrams();
        this.pricePaise   = p.getPricePaise();
        this.mrpPaise     = p.getMrpPaise();
        this.stockQty     = p.getStockQty();
        this.images       = p.getImages();
        this.tags         = p.getTags();
        this.isActive     = p.isActive();
        this.isFeatured   = p.isFeatured();
        this.categoryId   = p.getCategoryId();
        this.categoryName = p.getCategoryName();
        this.categorySlug = p.getCategorySlug();
    }

    // ── Paginated list wrapper ────────────────────────────────────────────

    /**
     * Wraps a page of products with pagination metadata.
     */
    public static class ProductListResponse {
        public final List<ProductResponse> products;
        public final int                   total;
        public final int                   page;
        public final int                   size;
        public final int                   totalPages;

        public ProductListResponse(List<ProductResponse> products,
                                   int total, int page, int size) {
            this.products   = products;
            this.total      = total;
            this.page       = page;
            this.size       = size;
            this.totalPages = (size > 0) ? (int) Math.ceil((double) total / size) : 0;
        }
    }
}
