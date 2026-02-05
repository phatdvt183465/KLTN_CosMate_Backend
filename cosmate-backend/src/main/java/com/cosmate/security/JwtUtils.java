package com.cosmate.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtils {

    private final Key key;
    private final long expirationMs;

    // Read secret and optional expiration from application.properties via constructor injection
    public JwtUtils(@Value("${jwt.secret:}") String jwtSecret,
                    @Value("${jwt.expiration-ms:604800000}") long expirationMs) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("jwt.secret is missing or empty. Vui lòng cấu hình jwt.secret trong application.properties");
        }

        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);

        // If the provided secret looks like Base64, try to decode it (common practice)
        byte[] maybeDecoded = null;
        try {
            maybeDecoded = Base64.getDecoder().decode(jwtSecret);
        } catch (IllegalArgumentException ignored) {
            // not base64 - ignore
        }

        if (maybeDecoded != null && maybeDecoded.length >= 32) {
            secretBytes = maybeDecoded;
        }

        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret phải có ít nhất 32 bytes (sử dụng chuỗi ngẫu nhiên dài hoặc một khóa Base64 256-bit)");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }

}
