package com.knowflow.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class JwtSecretSecurityTest {

    private static final String VALID_SECRET = "super-secure-production-ready-jwt-signing-key-minimum-256-bits-32bytes";
    private static final String SHORT_SECRET = "short-weak-key";

    @Test
    @DisplayName("Should throw IllegalStateException when secret is null")
    public void testThrowsWhenSecretIsNull() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(null, 86400000L, 604800000L)
        );
        assertTrue(ex.getMessage().contains("JWT configuration error: JWT_SECRET environment variable is missing or empty"));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when secret is empty or blank")
    public void testThrowsWhenSecretIsEmptyOrBlank() {
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("", 86400000L, 604800000L)
        );
        assertTrue(ex1.getMessage().contains("JWT configuration error: JWT_SECRET environment variable is missing or empty"));

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("   ", 86400000L, 604800000L)
        );
        assertTrue(ex2.getMessage().contains("JWT configuration error: JWT_SECRET environment variable is missing or empty"));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when secret is less than 256 bits (32 bytes)")
    public void testThrowsWhenSecretIsLessThan256Bits() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(SHORT_SECRET, 86400000L, 604800000L)
        );
        assertTrue(ex.getMessage().contains("does not meet the minimum security requirements for HMAC-SHA algorithms"));
        assertTrue(ex.getMessage().contains("must be at least 256 bits (32 bytes)"));
        // Ensure secret value itself is NEVER included in the exception message
        assertFalse(ex.getMessage().contains(SHORT_SECRET), "Secret value must not be leaked in exception message!");
    }

    @Test
    @DisplayName("Should initialize cleanly and generate/validate tokens when secret is 256+ bits")
    public void testInitializesCleanlyWithValidSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, 86400000L, 604800000L);
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "architect@enterprise.com", "USER");

        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals(userId, provider.getUserIdFromToken(token));
        assertEquals("architect@enterprise.com", provider.getEmailFromToken(token));
    }
}
