package com.grobird.psf.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protects /api/developer/** (OpenAPI docs + Swagger UI) with X-Developer-Secret header or cookie.
 * Accepts secret via header, or query param developer_secret (GET). Sets cookie so Swagger UI can load the spec.
 */
@Component
public class DeveloperSecretFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Developer-Secret";
    private static final String QUERY_PARAM = "developer_secret";
    private static final String COOKIE_NAME = "psf_docs";
    private static final int COOKIE_MAX_AGE_SECONDS = 3600;

    @Value("${developer.secret:}")
    private String developerSecret;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/developer/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String secret = request.getHeader(HEADER);
        if (secret == null && "GET".equalsIgnoreCase(request.getMethod())) {
            secret = request.getParameter(QUERY_PARAM);
        }
        boolean validSecret = developerSecret != null && !developerSecret.isEmpty() && developerSecret.equals(secret);
        boolean hasValidCookie = hasValidCookie(request);

        if (validSecret) {
            addDocsCookie(response);
            filterChain.doFilter(request, response);
            return;
        }
        if (hasValidCookie) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized: missing or invalid X-Developer-Secret (or " + QUERY_PARAM + " for GET)\"}");
    }

    private boolean hasValidCookie(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && "1".equals(c.getValue())) return true;
        }
        return false;
    }

    private void addDocsCookie(HttpServletResponse response) {
        String cookieValue = COOKIE_NAME + "=1; Path=/api/developer; Max-Age=" + COOKIE_MAX_AGE_SECONDS + "; HttpOnly; SameSite=Lax";
        response.addHeader("Set-Cookie", cookieValue);
    }
}
