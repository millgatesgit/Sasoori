package com.sasoori.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mirrors the {@code products} table row.
 * {@code images} maps to the JSONB column; {@code tags} maps to the TEXT[] column.
 */
public class Product {

    private String         id;           // UUID
    private int            categoryId;
    private String         categoryName;
    private String         categorySlug;
    private String         name;
    private String         slug;
    private String         sku;
    private String         description;
    private String         ingredients;
    private int            weightGrams;
    private int            pricePaise;
    private int            mrpPaise;
    private int            stockQty;
    private List<String>   images;       // JSONB → ["url1","url2"]
    private List<String>   tags;         // TEXT[]
    private boolean        isActive;
    private boolean        isFeatured;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Product() {}

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()             { return id; }
    public void   setId(String id)    { this.id = id; }

    public int  getCategoryId()               { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName()                     { return categoryName; }
    public void   setCategoryName(String categoryName)  { this.categoryName = categoryName; }

    public String getCategorySlug()                     { return categorySlug; }
    public void   setCategorySlug(String categorySlug)  { this.categorySlug = categorySlug; }

    public String getName()             { return name; }
    public void   setName(String name)  { this.name = name; }

    public String getSlug()             { return slug; }
    public void   setSlug(String slug)  { this.slug = slug; }

    public String getSku()            { return sku; }
    public void   setSku(String sku)  { this.sku = sku; }

    public String getDescription()                    { return description; }
    public void   setDescription(String description)  { this.description = description; }

    public String getIngredients()                    { return ingredients; }
    public void   setIngredients(String ingredients)  { this.ingredients = ingredients; }

    public int  getWeightGrams()                { return weightGrams; }
    public void setWeightGrams(int weightGrams) { this.weightGrams = weightGrams; }

    public int  getPricePaise()               { return pricePaise; }
    public void setPricePaise(int pricePaise) { this.pricePaise = pricePaise; }

    public int  getMrpPaise()             { return mrpPaise; }
    public void setMrpPaise(int mrpPaise) { this.mrpPaise = mrpPaise; }

    public int  getStockQty()             { return stockQty; }
    public void setStockQty(int stockQty) { this.stockQty = stockQty; }

    public List<String> getImages()               { return images; }
    public void         setImages(List<String> v) { this.images = v; }

    public List<String> getTags()               { return tags; }
    public void         setTags(List<String> v) { this.tags = v; }

    public boolean isActive()                  { return isActive; }
    public void    setActive(boolean active)   { this.isActive = active; }

    public boolean isFeatured()                   { return isFeatured; }
    public void    setFeatured(boolean featured)  { this.isFeatured = featured; }

    public OffsetDateTime getCreatedAt()                 { return createdAt; }
    public void           setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt()                 { return updatedAt; }
    public void           setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
