package com.knowflow.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String CLAIM_VERSION = "version";

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${knowflow.security.jwt.secret}") String secret,
            @Value("${knowflow.security.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${knowflow.security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs
    ) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                "JWT configuration error: JWT_SECRET environment variable is missing or empty. "
                + "A secure signing key of at least 256 bits (32 bytes) must be provided via the JWT_SECRET environment variable."
            );
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT configuration error: JWT_SECRET does not meet the minimum security requirements "
                + "for HMAC-SHA algorithms. Key size is " + (keyBytes.length * 8)
                + " bits, but must be at least 256 bits (32 bytes)."
            );
        }
        try {
            this.key = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("JWT configuration error: Invalid JWT_SECRET provided.", ex);
        }
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        return generateAccessToken(userId, email, role, 1);
    }

    public String generateAccessToken(UUID userId, String email, String role, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return generateRefreshToken(userId, 1);
    }

    public String generateRefreshToken(UUID userId, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (Exception ex) {
            return false;
        }
    }

    public String getTokenType(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return claims.get(CLAIM_TYPE, String.class);
        } catch (Exception ex) {
            return null;
        }
    }

    public Integer getTokenVersion(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Object versionObj = claims.get(CLAIM_VERSION);
            if (versionObj instanceof Number num) {
                return num.intValue();
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.get("email", String.class);
    }
}
