package com.knowflow.ai.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class JwtTokenProviderTest {

    private static final String SECRET = "super-secure-production-ready-jwt-signing-key-minimum-256-bits-32bytes";
    private JwtTokenProvider provider;
    private SecretKey signingKey;

    @BeforeEach
    public void setup() {
        provider = new JwtTokenProvider(SECRET, 86400000L, 604800000L);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Access token has type 'access', passes validateAccessToken, and fails validateRefreshToken")
    public void testAccessTokenTypeValidation() {
        UUID userId = UUID.randomUUID();
        String accessToken = provider.generateAccessToken(userId, "user@enterprise.com", "USER");

        assertNotNull(accessToken);
        assertEquals(JwtTokenProvider.TYPE_ACCESS, provider.getTokenType(accessToken));
        assertTrue(provider.validateAccessToken(accessToken));
        assertFalse(provider.validateRefreshToken(accessToken));
        assertTrue(provider.validateToken(accessToken));
    }

    @Test
    @DisplayName("Refresh token has type 'refresh', passes validateRefreshToken, and fails validateAccessToken")
    public void testRefreshTokenTypeValidation() {
        UUID userId = UUID.randomUUID();
        String refreshToken = provider.generateRefreshToken(userId);

        assertNotNull(refreshToken);
        assertEquals(JwtTokenProvider.TYPE_REFRESH, provider.getTokenType(refreshToken));
        assertTrue(provider.validateRefreshToken(refreshToken));
        assertFalse(provider.validateAccessToken(refreshToken));
        assertTrue(provider.validateToken(refreshToken));
    }

    @Test
    @DisplayName("Token with missing type claim is rejected by both validateAccessToken and validateRefreshToken")
    public void testTokenWithMissingTypeClaimRejected() {
        UUID userId = UUID.randomUUID();
        // Create token with no "type" claim (simulates pre-hardening / legacy token)
        String legacyToken = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "legacy@enterprise.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();

        assertNull(provider.getTokenType(legacyToken));
        assertFalse(provider.validateAccessToken(legacyToken));
        assertFalse(provider.validateRefreshToken(legacyToken));
        assertTrue(provider.validateToken(legacyToken)); // Valid cryptographic signature, but not an accepted type
    }

    @Test
    @DisplayName("Token with unknown or spoofed type claim is rejected by both validators")
    public void testTokenWithUnknownTypeClaimRejected() {
        UUID userId = UUID.randomUUID();
        String unknownTypeToken = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "unknown@enterprise.com")
                .claim(JwtTokenProvider.CLAIM_TYPE, "admin_session")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();

        assertEquals("admin_session", provider.getTokenType(unknownTypeToken));
        assertFalse(provider.validateAccessToken(unknownTypeToken));
        assertFalse(provider.validateRefreshToken(unknownTypeToken));
        assertTrue(provider.validateToken(unknownTypeToken));
    }

    @Test
    @DisplayName("Expired access and refresh tokens are rejected")
    public void testExpiredTokensRejected() {
        UUID userId = UUID.randomUUID();
        Date past = new Date(System.currentTimeMillis() - 10000);

        String expiredAccess = Jwts.builder()
                .subject(userId.toString())
                .claim(JwtTokenProvider.CLAIM_TYPE, JwtTokenProvider.TYPE_ACCESS)
                .issuedAt(new Date(System.currentTimeMillis() - 20000))
                .expiration(past)
                .signWith(signingKey)
                .compact();

        String expiredRefresh = Jwts.builder()
                .subject(userId.toString())
                .claim(JwtTokenProvider.CLAIM_TYPE, JwtTokenProvider.TYPE_REFRESH)
                .issuedAt(new Date(System.currentTimeMillis() - 20000))
                .expiration(past)
                .signWith(signingKey)
                .compact();

        assertFalse(provider.validateAccessToken(expiredAccess));
        assertFalse(provider.validateRefreshToken(expiredRefresh));
        assertFalse(provider.validateToken(expiredAccess));
        assertFalse(provider.validateToken(expiredRefresh));
    }

    @Test
    @DisplayName("Tokens signed with invalid key are rejected")
    public void testTokensWithInvalidSignatureRejected() {
        String otherSecret = "another-completely-different-signing-secret-key-32-bytes";
        SecretKey otherKey = Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8));
        UUID userId = UUID.randomUUID();

        String invalidAccess = Jwts.builder()
                .subject(userId.toString())
                .claim(JwtTokenProvider.CLAIM_TYPE, JwtTokenProvider.TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(otherKey)
                .compact();

        assertFalse(provider.validateAccessToken(invalidAccess));
        assertFalse(provider.validateRefreshToken(invalidAccess));
        assertFalse(provider.validateToken(invalidAccess));
    }

    @Test
    @DisplayName("Null, empty, or malformed strings return false without throwing exceptions")
    public void testNullOrBlankTokensHandledSafely() {
        assertFalse(provider.validateAccessToken(null));
        assertFalse(provider.validateAccessToken(""));
        assertFalse(provider.validateAccessToken("   "));
        assertFalse(provider.validateAccessToken("not.a.valid.jwt.token"));

        assertFalse(provider.validateRefreshToken(null));
        assertFalse(provider.validateRefreshToken(""));
        assertFalse(provider.validateRefreshToken("   "));
        assertFalse(provider.validateRefreshToken("not.a.valid.jwt.token"));

        assertNull(provider.getTokenType(null));
        assertNull(provider.getTokenType(""));
        assertNull(provider.getTokenType("invalid.token"));
    }

    @Test
    @DisplayName("Token version is correctly embedded and extracted from access and refresh tokens")
    public void testTokenVersionEmbeddingAndExtraction() {
        UUID userId = UUID.randomUUID();
        String accessToken = provider.generateAccessToken(userId, "versioned@enterprise.com", "USER", 42);
        String refreshToken = provider.generateRefreshToken(userId, 42);

        assertEquals(42, provider.getTokenVersion(accessToken));
        assertEquals(42, provider.getTokenVersion(refreshToken));

        // Default generators embed version 1
        String defaultAccess = provider.generateAccessToken(userId, "default@enterprise.com", "USER");
        String defaultRefresh = provider.generateRefreshToken(userId);

        assertEquals(1, provider.getTokenVersion(defaultAccess));
        assertEquals(1, provider.getTokenVersion(defaultRefresh));

        // Null / blank / malformed tokens return null version
        assertNull(provider.getTokenVersion(null));
        assertNull(provider.getTokenVersion(""));
        assertNull(provider.getTokenVersion("malformed.token"));
    }
}
