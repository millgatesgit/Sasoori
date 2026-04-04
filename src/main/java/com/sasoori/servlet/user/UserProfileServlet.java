package com.sasoori.servlet.user;

import com.sasoori.dao.UserDao;
import com.sasoori.dto.UpdateProfileRequest;
import com.sasoori.dto.UserResponse;
import com.sasoori.exception.ApiException;
import com.sasoori.model.User;
import com.sasoori.servlet.BaseServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET  /api/v1/user/profile  — return current user's profile
 * PUT  /api/v1/user/profile  — update name + phone
 *
 * userId is read from the JWT (set by JWTAuthFilter) — never from the request body.
 */
@WebServlet(urlPatterns = "/api/v1/user/profile")
public class UserProfileServlet extends BaseServlet {

    private UserDao userDao;

    @Override
    public void init() {
        userDao = (UserDao) getServletContext().getAttribute("userDao");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            User user = userDao.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("User not found"));
            sendSuccess(resp, new UserResponse(user));
        });
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            UpdateProfileRequest body = parseBody(req, UpdateProfileRequest.class);

            if (body.name == null || body.name.isBlank())
                throw ApiException.badRequest("MISSING_NAME", "Name is required");

            String name = body.name.trim();
            if (name.length() > 100)
                throw ApiException.badRequest("NAME_TOO_LONG", "Name must be under 100 characters");

            // Validate phone if provided
            String phone = (body.phone != null && !body.phone.isBlank()) ? body.phone.trim() : null;
            if (phone != null && !phone.matches("^\\+91[6-9]\\d{9}$"))
                throw ApiException.badRequest("INVALID_PHONE", "Enter a valid Indian mobile number (+91XXXXXXXXXX)");

            User updated = userDao.updateProfile(userId, name, phone);
            sendSuccess(resp, new UserResponse(updated));
        });
    }
}
