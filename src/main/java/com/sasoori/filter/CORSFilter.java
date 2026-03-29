package com.sasoori.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CORS filter — allows the configured frontend origin(s).
 * FRONTEND_URL may be a comma-separated list for dev (e.g. "http://localhost:3000,http://localhost:5500").
 * Must run before JWTAuthFilter so OPTIONS preflight requests are handled.
 */
@WebFilter(urlPatterns = "/api/v1/*", filterName = "CORSFilter")
public class CORSFilter implements Filter {

    private Set<String> allowedOrigins;

    @Override
    public void init(FilterConfig cfg) {
        String raw = cfg.getServletContext().getInitParameter("frontendUrl");
        if (raw == null || raw.isBlank()) raw = "http://localhost:5500";
        allowedOrigins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String origin = req.getHeader("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            resp.setHeader("Access-Control-Allow-Origin",      origin);
            resp.setHeader("Access-Control-Allow-Credentials", "true");
            resp.setHeader("Access-Control-Allow-Methods",
                    "GET, POST, PUT, DELETE, OPTIONS, PATCH");
            resp.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, Authorization, X-Requested-With");
            resp.setHeader("Access-Control-Max-Age", "86400");
            resp.setHeader("Vary", "Origin");
        }

        // Handle preflight — return 200 without further processing
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);
    }
}
