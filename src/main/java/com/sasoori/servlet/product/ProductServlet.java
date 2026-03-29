package com.sasoori.servlet.product;

import com.sasoori.dao.ProductDao;
import com.sasoori.dto.ProductResponse;
import com.sasoori.exception.ApiException;
import com.sasoori.model.Product;
import com.sasoori.service.ProductService;
import com.sasoori.servlet.BaseServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Product CRUD endpoints.
 *
 * <pre>
 *   GET  /api/v1/products                  → paginated list (public)
 *   GET  /api/v1/products/slug/{slug}       → single product by slug (public)
 *   GET  /api/v1/products/{id}              → single product by UUID (public)
 *   POST /api/v1/products                  → create product (ADMIN)
 *   PUT  /api/v1/products/{id}             → update product (ADMIN)
 *   DELETE /api/v1/products/{id}           → soft-delete product (ADMIN)
 * </pre>
 */
@WebServlet(urlPatterns = "/api/v1/products/*")
public class ProductServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(ProductServlet.class);

    private ProductService productService;

    @Override
    public void init() {
        productService = (ProductService) getServletContext().getAttribute("productService");
    }

    // ── GET ───────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String path = subPath(req);

            if (path.isEmpty() || path.equals("/")) {
                // List with optional filters
                String category = req.getParameter("category");
                String search   = req.getParameter("search");
                String tag      = req.getParameter("tag");
                String sort     = req.getParameter("sort");
                int    page     = parseIntParam(req, "page", 1);
                int    size     = parseIntParam(req, "size", 12);

                ProductDao.ProductPage result =
                        productService.listProducts(category, search, tag, sort, page, size);

                List<ProductResponse> dtos = result.products().stream()
                        .map(ProductResponse::new)
                        .collect(Collectors.toList());

                sendSuccess(resp, new ProductResponse.ProductListResponse(
                        dtos, result.total(), result.page(), result.size()));

            } else if (path.startsWith("/slug/")) {
                // By slug
                String slug = path.substring("/slug/".length());
                Product product = productService.getBySlug(slug);
                sendSuccess(resp, new ProductResponse(product));

            } else {
                // By UUID — strip leading "/"
                String id = path.substring(1);
                Product product = productService.getById(id);
                sendSuccess(resp, new ProductResponse(product));
            }
        });
    }

    // ── POST (create) ─────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            requireAdmin(req);
            Product body    = parseBody(req, Product.class);
            Product created = productService.create(body);
            sendSuccess(resp, 201, new ProductResponse(created));
        });
    }

    // ── PUT (update) ──────────────────────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            requireAdmin(req);
            String path = subPath(req);
            if (path.isEmpty() || path.equals("/")) {
                throw ApiException.badRequest("MISSING_ID", "Product id is required in path");
            }
            String id    = path.substring(1);
            Product body = parseBody(req, Product.class);
            body.setId(id);
            Product updated = productService.update(body);
            sendSuccess(resp, new ProductResponse(updated));
        });
    }

    // ── DELETE (soft-delete) ──────────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            requireAdmin(req);
            String path = subPath(req);
            if (path.isEmpty() || path.equals("/")) {
                throw ApiException.badRequest("MISSING_ID", "Product id is required in path");
            }
            String id = path.substring(1);
            productService.delete(id);
            sendSuccess(resp, java.util.Map.of("deleted", true));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Returns the portion of the request URI after {@code /api/v1/products}.
     */
    private String subPath(HttpServletRequest req) {
        String uri  = req.getRequestURI();
        String base = req.getContextPath() + "/api/v1/products";
        String sub  = uri.startsWith(base) ? uri.substring(base.length()) : "";
        return sub.isEmpty() ? "" : sub;
    }

    private int parseIntParam(HttpServletRequest req, String name, int defaultValue) {
        String raw = req.getParameter(name);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Checks that the authenticated user has the ADMIN role.
     * Relies on JWTAuthFilter having set the {@code userRole} request attribute.
     */
    private void requireAdmin(HttpServletRequest req) {
        requireUserId(req); // ensures auth is present
        if (!"ADMIN".equals(getUserRole(req))) {
            throw ApiException.forbidden("Admin access required");
        }
    }
}
