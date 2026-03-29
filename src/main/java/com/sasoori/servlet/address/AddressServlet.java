package com.sasoori.servlet.address;

import com.sasoori.dao.AddressDao;
import com.sasoori.exception.ApiException;
import com.sasoori.model.Address;
import com.sasoori.servlet.BaseServlet;
import com.sasoori.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

/**
 * Address endpoints:
 * <pre>
 *   GET    /api/v1/addresses         — list user's addresses
 *   POST   /api/v1/addresses         — create address
 *   PUT    /api/v1/addresses/{id}    — update address
 *   DELETE /api/v1/addresses/{id}    — delete address
 *   POST   /api/v1/addresses/{id}/default — set as default
 * </pre>
 * All routes require authentication.
 */
@WebServlet(urlPatterns = "/api/v1/addresses/*")
public class AddressServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(AddressServlet.class);

    private AddressDao addressDao;

    @Override
    public void init() {
        addressDao = (AddressDao) getServletContext().getAttribute("addressDao");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            sendSuccess(resp, addressDao.findByUserId(userId));
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = subPath(req);
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            if (path.isEmpty() || "/".equals(path)) {
                Address body = parseBody(req, Address.class);
                validateAddress(body);
                body.setUserId(userId);
                Address created = addressDao.create(body);
                sendSuccess(resp, 201, created);
            } else if (path.matches("/\\d+/default")) {
                int id = parseAddressId(path.substring(1, path.lastIndexOf("/default")));
                addressDao.setDefault(id, userId);
                sendSuccess(resp, Map.of("message", "Default address updated"));
            } else {
                throw ApiException.notFound("Unknown address endpoint: " + path);
            }
        });
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            int id = parseAddressId(subPath(req).substring(1));
            Address body = parseBody(req, Address.class);
            validateAddress(body);
            body.setId(id);
            body.setUserId(userId);
            Address updated = addressDao.update(body);
            sendSuccess(resp, updated);
        });
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            int id = parseAddressId(subPath(req).substring(1));
            addressDao.delete(id, userId);
            sendSuccess(resp, Map.of("message", "Address deleted"));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void validateAddress(Address a) {
        if (blank(a.getName()))    throw ApiException.badRequest("MISSING_NAME",    "name is required");
        if (blank(a.getPhone()))   throw ApiException.badRequest("MISSING_PHONE",   "phone is required");
        if (blank(a.getLine1()))   throw ApiException.badRequest("MISSING_LINE1",   "line1 is required");
        if (blank(a.getCity()))    throw ApiException.badRequest("MISSING_CITY",    "city is required");
        if (blank(a.getState()))   throw ApiException.badRequest("MISSING_STATE",   "state is required");
        if (blank(a.getPincode())) throw ApiException.badRequest("MISSING_PINCODE", "pincode is required");
        if (!a.getPincode().matches("\\d{6}"))
            throw ApiException.badRequest("INVALID_PINCODE", "Pincode must be 6 digits");
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static int parseAddressId(String segment) {
        try {
            return Integer.parseInt(segment.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("INVALID_ADDRESS_ID", "Invalid address id");
        }
    }

    private static String subPath(HttpServletRequest req) {
        String uri   = req.getRequestURI();
        String ctx   = req.getContextPath();
        String after = uri.substring(ctx.length());
        int    idx   = after.indexOf("/api/v1/addresses");
        return idx >= 0 ? after.substring(idx + "/api/v1/addresses".length()) : after;
    }
}
