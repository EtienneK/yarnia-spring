package com.etiennek.yarnia.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP Basic auth for /admin/**. When no admin password is configured the
 * dashboard doesn't exist at all (404) - safe by default. Any username is
 * accepted; only the password matters (constant-time comparison).
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    @Value("${yarnia.admin-password:}")
    private String adminPassword;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/admin")) {
            chain.doFilter(request, response);
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Basic ")) {
            try {
                final var decoded = new String(
                        Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
                final var colon = decoded.indexOf(':');
                final var password = colon >= 0 ? decoded.substring(colon + 1) : "";
                if (MessageDigest.isEqual(
                        password.getBytes(StandardCharsets.UTF_8),
                        adminPassword.getBytes(StandardCharsets.UTF_8))) {
                    chain.doFilter(request, response);
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // malformed base64 -> treat as unauthorized
            }
        }

        response.setHeader("WWW-Authenticate", "Basic realm=\"yarnia-admin\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
