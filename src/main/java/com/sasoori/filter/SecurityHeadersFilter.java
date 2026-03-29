package com.sasoori.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Adds security headers to every API response.
 */
@WebFilter(urlPatterns = "/api/v1/*", filterName = "SecurityHeadersFilter")
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse resp = (HttpServletResponse) response;

        resp.setHeader("X-Content-Type-Options",   "nosniff");
        resp.setHeader("X-Frame-Options",           "DENY");
        resp.setHeader("Referrer-Policy",           "strict-origin-when-cross-origin");
        resp.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        // Permissive CSP for API — tightened on frontend via Nginx
        resp.setHeader("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'");

        chain.doFilter(request, response);
    }
}
