package com.sasoori.servlet.category;

import com.sasoori.dao.CategoryDao;
import com.sasoori.model.Category;
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
 * Read-only category endpoints.
 *
 * <pre>
 *   GET /api/v1/categories        → all active categories
 *   GET /api/v1/categories/{slug} → single category by slug
 * </pre>
 */
@WebServlet(urlPatterns = "/api/v1/categories/*")
public class CategoryServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(CategoryServlet.class);

    /** Thin response record serialised to JSON. */
    private record CategoryResponse(int id, String name, String slug,
                                    String description, String imageUrl, int sortOrder) {}

    private CategoryDao categoryDao;

    @Override
    public void init() {
        categoryDao = (CategoryDao) getServletContext().getAttribute("categoryDao");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String path = subPath(req); // "" or "/{slug}"

            if (path.isEmpty() || path.equals("/")) {
                // List all active categories
                List<Category> categories = categoryDao.findAllActive();
                List<CategoryResponse> response = categories.stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList());
                sendSuccess(resp, response);
            } else {
                // Single category by slug — strip leading "/"
                String slug = path.substring(1);
                Category cat = categoryDao.findBySlug(slug)
                        .orElseThrow(() -> com.sasoori.exception.ApiException.notFound(
                                "Category not found: " + slug));
                sendSuccess(resp, toResponse(cat));
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Returns the portion of the request URI after {@code /api/v1/categories}.
     * E.g. {@code /api/v1/categories/sweets} → {@code /sweets}.
     */
    private String subPath(HttpServletRequest req) {
        String uri     = req.getRequestURI();
        String context = req.getContextPath();          // "" in ROOT context
        String base    = context + "/api/v1/categories";
        String sub     = uri.startsWith(base) ? uri.substring(base.length()) : "";
        return sub.isEmpty() ? "" : sub;
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(), c.getName(), c.getSlug(),
                c.getDescription(), c.getImageUrl(), c.getSortOrder());
    }
}
