package com.sasoori.service;

import com.sasoori.dao.CategoryDao;
import com.sasoori.dao.ProductDao;
import com.sasoori.exception.ApiException;
import com.sasoori.model.Product;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business logic for products.
 * Adds a simple 60-second in-memory cache over the paginated list query.
 */
public class ProductService {

    private static final Logger log = LogManager.getLogger(ProductService.class);

    private static final long CACHE_TTL_MS = 60_000L;

    /** Cached page entry. */
    private record CachedPage(ProductDao.ProductPage page, long expiresAt) {}

    /** Per-JVM cache; keyed by all list-query params joined with "|". */
    private final ConcurrentHashMap<String, CachedPage> CACHE = new ConcurrentHashMap<>();

    private final ProductDao  productDao;
    private final CategoryDao categoryDao;

    public ProductService(ProductDao productDao, CategoryDao categoryDao) {
        this.productDao  = productDao;
        this.categoryDao = categoryDao;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Paginated product listing with optional category / search / tag filters.
     * Results are cached for {@value #CACHE_TTL_MS} ms.
     */
    public ProductDao.ProductPage listProducts(String categorySlug, String search,
                                               String tag, String sort,
                                               int page, int size) {
        String key = cacheKey(categorySlug, search, tag, sort, page, size);
        CachedPage cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            log.debug("Cache hit for key={}", key);
            return cached.page();
        }

        try {
            ProductDao.ProductPage result = productDao.list(categorySlug, search, tag, sort, page, size);
            CACHE.put(key, new CachedPage(result, System.currentTimeMillis() + CACHE_TTL_MS));
            return result;
        } catch (SQLException e) {
            log.error("listProducts failed: {}", e.getMessage(), e);
            throw ApiException.internal("Failed to fetch products");
        }
    }

    /**
     * Returns a single active product by slug, or throws 404.
     */
    public Product getBySlug(String slug) {
        try {
            return productDao.findBySlug(slug)
                    .orElseThrow(() -> ApiException.notFound("Product not found: " + slug));
        } catch (SQLException e) {
            log.error("getBySlug failed slug={}: {}", slug, e.getMessage(), e);
            throw ApiException.internal("Failed to fetch product");
        }
    }

    /**
     * Returns a single active product by UUID, or throws 404.
     */
    public Product getById(String id) {
        try {
            return productDao.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        } catch (SQLException e) {
            log.error("getById failed id={}: {}", id, e.getMessage(), e);
            throw ApiException.internal("Failed to fetch product");
        }
    }

    /**
     * Validates and creates a new product. Invalidates list cache.
     */
    public Product create(Product p) {
        validateProduct(p);
        try {
            Product created = productDao.create(p);
            CACHE.clear();
            return created;
        } catch (SQLException e) {
            log.error("create product failed: {}", e.getMessage(), e);
            throw ApiException.internal("Failed to create product");
        }
    }

    /**
     * Validates and updates an existing product. Invalidates list cache.
     */
    public Product update(Product p) {
        validateProduct(p);
        try {
            Product updated = productDao.update(p);
            CACHE.clear();
            return updated;
        } catch (SQLException e) {
            log.error("update product failed id={}: {}", p.getId(), e.getMessage(), e);
            throw ApiException.internal("Failed to update product");
        }
    }

    /**
     * Soft-deletes a product. Invalidates list cache.
     */
    public void delete(String id) {
        try {
            productDao.softDelete(id);
            CACHE.clear();
        } catch (SQLException e) {
            log.error("delete product failed id={}: {}", id, e.getMessage(), e);
            throw ApiException.internal("Failed to delete product");
        }
    }

    /**
     * Clears the in-memory product list cache.
     * Call after any admin write operation that bypasses service-layer methods.
     */
    public void invalidateCache() {
        CACHE.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void validateProduct(Product p) {
        if (p.getName() == null || p.getName().isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Product name is required");
        }
        if (p.getPricePaise() <= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Product price must be greater than 0");
        }
        if (p.getCategoryId() <= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A valid category_id is required");
        }
    }

    private String cacheKey(String a, String b, String c, String d, int e, int f) {
        return (a == null ? "" : a) + "|" +
               (b == null ? "" : b) + "|" +
               (c == null ? "" : c) + "|" +
               (d == null ? "" : d) + "|" +
               e + "|" + f;
    }
}
