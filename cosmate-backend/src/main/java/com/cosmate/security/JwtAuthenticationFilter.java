package com.cosmate.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().toLowerCase();

        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")
                || path.startsWith("/configuration")
                || path.equals("/swagger-ui.html")
                // NOTE: do NOT skip the JWT filter for all /api/auth paths because
                // some auth endpoints (e.g. /api/auth/qr-approve) must be authenticated.
                // Keep other public prefixes here.
                || path.startsWith("/api/public")
                || path.startsWith("/api/events")
                || path.startsWith("/api/vnpay/return")
                || "options".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Log incoming request method and URI for diagnosis
        logger.debug("Incoming request {} {}", request.getMethod(), request.getRequestURI());
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            String masked = header.length() > 30 ? header.substring(0, 30) + "..." : header;
            logger.debug("Authorization header present (masked)='{}'", masked);
        }

        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            logger.debug("JWT header present, raw header='{}'", header);

            // Normalize: strip any number of leading "Bearer " prefixes (case-insensitive)
            token = token.trim();
            while (token.toLowerCase().startsWith("bearer ")) {
                token = token.substring(7).trim();
            }

            logger.debug("Normalized token (first 20 chars)='{}'", token.length() > 20 ? token.substring(0,20) + "..." : token);
            try {
                var claims = jwtUtils.parse(token).getBody();
                String userId = claims.getSubject();
                Object rolesObj = claims.get("roles");
                List<String> roles = null;
                if (rolesObj instanceof List) {
                    roles = ((List<?>) rolesObj).stream().map(Object::toString).collect(Collectors.toList());
                }
                logger.debug("Parsed JWT for userId={} roles={}", userId, roles);
                List<SimpleGrantedAuthority> authorities = (roles != null)
                        ? roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()
                        : Collections.emptyList();
                // Prefer setting the principal as Integer when subject is numeric to simplify
                // downstream code that expects an integer user id.
                Object principal = userId;
                try {
                    principal = Integer.valueOf(userId);
                } catch (Exception ignored) {
                    // keep principal as string if not numeric
                }
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                logger.debug("Invalid/expired JWT: {}", e.getMessage());
                // invalid token → không set authentication, Security sẽ xử lý
            }
        } else {
            logger.debug("No Bearer Authorization header present");
        }
        filterChain.doFilter(request, response);
    }
}
