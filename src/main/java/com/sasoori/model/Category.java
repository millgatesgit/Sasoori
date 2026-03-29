package com.sasoori.model;

/**
 * Mirrors the {@code categories} table row.
 */
public class Category {

    private int     id;
    private String  name;
    private String  slug;
    private String  description;
    private String  imageUrl;
    private int     sortOrder;
    private boolean isActive;

    public Category() {}

    // ── Getters / Setters ─────────────────────────────────────────────────

    public int  getId()           { return id; }
    public void setId(int id)     { this.id = id; }

    public String getName()             { return name; }
    public void   setName(String name)  { this.name = name; }

    public String getSlug()             { return slug; }
    public void   setSlug(String slug)  { this.slug = slug; }

    public String getDescription()                    { return description; }
    public void   setDescription(String description)  { this.description = description; }

    public String getImageUrl()                   { return imageUrl; }
    public void   setImageUrl(String imageUrl)    { this.imageUrl = imageUrl; }

    public int  getSortOrder()              { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive()                  { return isActive; }
    public void    setActive(boolean active)   { this.isActive = active; }
}
