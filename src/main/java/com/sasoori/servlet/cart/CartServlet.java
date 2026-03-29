package com.sasoori.servlet.cart;

import com.sasoori.dao.CartDao;
import com.sasoori.dto.CartItemRequest;
import com.sasoori.dto.CartResponse;
import com.sasoori.exception.ApiException;
import com.sasoori.model.CartItem;
import com.sasoori.servlet.BaseServlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * REST endpoint for the shopping cart.
 *
 * <pre>
 * GET    /api/v1/cart              → fetch cart
 * POST   /api/v1/cart/items        → add / update item
 * PUT    /api/v1/cart/items/{pid}  → update item quantity
 * DELETE /api/v1/cart/items/{pid}  → remove item
 * DELETE /api/v1/cart              → clear cart
 * </pre>
 *
 * All routes require a valid JWT (userId injected by JWTAuthFilter).
 */
@WebServlet(urlPatterns = "/api/v1/cart/*")
public class CartServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(CartServlet.class);

    private static final String BASE_PATH = "/api/v1/cart";

    private CartDao cartDao;

    @Override
    public void init(ServletConfig config) throws jakarta.servlet.ServletException {
        super.init(config);
        ServletContext ctx = config.getServletContext();
        cartDao = (CartDao) ctx.getAttribute("cartDao");
        if (cartDao == null) {
            throw new jakarta.servlet.ServletException("cartDao not found in ServletContext");
        }
    }

    // ── HTTP methods ──────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId  = requireUserId(req);
            String subPath = subPath(req);

            if (subPath.isEmpty() || subPath.equals("/")) {
                // GET /api/v1/cart
                List<CartItem> items = cartDao.getCartItems(userId);
                sendSuccess(resp, new CartResponse(items));
            } else {
                throw ApiException.notFound("Route not found");
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId  = requireUserId(req);
            String subPath = subPath(req);

            if (subPath.equals("/items") || subPath.equals("/items/")) {
                // POST /api/v1/cart/items
                CartItemRequest body = parseBody(req, CartItemRequest.class);
                validateAddRequest(body);

                cartDao.addOrUpdateItem(userId, body.productId.trim(), body.quantity);

                List<CartItem> items = cartDao.getCartItems(userId);
                sendSuccess(resp, 201, new CartResponse(items));
            } else {
                throw ApiException.notFound("Route not found");
            }
        });
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId     = requireUserId(req);
            String subPath    = subPath(req);
            String productId  = extractProductId(subPath, "/items/");

            // PUT /api/v1/cart/items/{pid}
            CartItemRequest body = parseBody(req, CartItemRequest.class);
            validateQuantity(body.quantity);

            cartDao.addOrUpdateItem(userId, productId, body.quantity);

            List<CartItem> items = cartDao.getCartItems(userId);
            sendSuccess(resp, new CartResponse(items));
        });
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId  = requireUserId(req);
            String subPath = subPath(req);

            if (subPath.isEmpty() || subPath.equals("/")) {
                // DELETE /api/v1/cart
                cartDao.clearCart(userId);
                List<CartItem> items = cartDao.getCartItems(userId);
                sendSuccess(resp, new CartResponse(items));

            } else if (subPath.startsWith("/items/")) {
                // DELETE /api/v1/cart/items/{pid}
                String productId = extractProductId(subPath, "/items/");
                cartDao.removeItem(userId, productId);
                List<CartItem> items = cartDao.getCartItems(userId);
                sendSuccess(resp, new CartResponse(items));

            } else {
                throw ApiException.notFound("Route not found");
            }
        });
    }

    // ── Validation ────────────────────────────────────────────────────────

    private void validateAddRequest(CartItemRequest body) {
        if (body.productId == null || body.productId.isBlank()) {
            throw ApiException.badRequest("INVALID_PRODUCT_ID", "productId is required");
        }
        validateQuantity(body.quantity);
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Quantity must be at least 1");
        }
        if (quantity > 50) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Maximum 50 units per item");
        }
    }

    // ── Path helpers ──────────────────────────────────────────────────────

    /**
     * Strips the context path and {@value #BASE_PATH} prefix from the request URI,
     * returning the remainder (e.g. {@code ""}, {@code "/items"}, {@code "/items/uuid"}).
     */
    private String subPath(HttpServletRequest req) {
        String uri         = req.getRequestURI();
        String contextPath = req.getContextPath();  // e.g. "" or "/app"
        String withoutCtx  = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        if (withoutCtx.startsWith(BASE_PATH)) {
            return withoutCtx.substring(BASE_PATH.length());
        }
        return withoutCtx;
    }

    /**
     * Extracts the trailing segment after {@code prefix} from {@code subPath}.
     * Throws 404 if nothing follows the prefix.
     */
    private String extractProductId(String subPath, String prefix) {
        if (!subPath.startsWith(prefix)) {
            throw ApiException.notFound("Route not found");
        }
        String id = subPath.substring(prefix.length());
        if (id.isBlank()) {
            throw ApiException.badRequest("MISSING_PRODUCT_ID", "Product ID is required in the path");
        }
        return id;
    }
}
