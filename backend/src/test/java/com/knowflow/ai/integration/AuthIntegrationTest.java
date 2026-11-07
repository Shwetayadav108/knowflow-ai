package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.ForgotPasswordRequest;
import com.knowflow.ai.auth.LoginRequest;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.auth.ResetPasswordRequest;
import com.knowflow.ai.email.EmailService;
import com.knowflow.ai.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @SpyBean
    private EmailService emailService;

    @Value("${knowflow.security.jwt.secret}")
    private String jwtSecret;

    @Test
    public void testCompleteAuthFlow() throws Exception {
        String email = "sarah.connor@enterprise.io";
        String password = "SecurePassword123!";

        // 1. Register
        RegisterRequest registerReq = new RegisterRequest("Sarah Connor", email, password);
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andReturn();

        JsonNode regJson = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String token = regJson.path("data").path("token").asText();
        String refreshToken = regJson.path("data").path("refreshToken").asText();

        // 2. Login
        LoginRequest loginReq = new LoginRequest(email, password);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // 3. Current user me endpoint
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email));

        // 4. Refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

        // 5. Forgot password (does not leak email existence)
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("random.unknown.user@enterprise.io");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If the email exists in our system, password reset instructions have been sent."));
    }

    @Test
    @DisplayName("Access token submitted to /auth/refresh endpoint is rejected with HTTP 401")
    public void testAccessTokenRejectedAtRefreshEndpoint() throws Exception {
        String email = "john.reese-" + UUID.randomUUID() + "@enterprise.io";
        RegisterRequest registerReq = new RegisterRequest("John Reese", email, "Password123!");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode regJson = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String accessToken = regJson.path("data").path("token").asText();

        // Submitting access token to refresh endpoint must fail with 401 Unauthorized
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", accessToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Refresh token submitted as Bearer token to normal API endpoint is rejected with HTTP 401")
    public void testRefreshTokenRejectedAsBearerTokenAtProtectedEndpoint() throws Exception {
        String email = "harold.finch-" + UUID.randomUUID() + "@enterprise.io";
        RegisterRequest registerReq = new RegisterRequest("Harold Finch", email, "Password123!");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode regJson = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String refreshToken = regJson.path("data").path("refreshToken").asText();

        // Submitting refresh token to normal protected API endpoint must fail with 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token with missing type claim is rejected at both API authentication and refresh endpoints")
    public void testTokenWithMissingTypeClaimRejectedAtBothEndpoints() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        UUID userId = UUID.randomUUID();

        // Token without 'type' claim
        String legacyToken = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "legacy.user@enterprise.io")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        // 1. Rejected as API bearer token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isUnauthorized());

        // 2. Rejected as refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", legacyToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Token with unknown or spoofed type claim is rejected at both endpoints")
    public void testTokenWithUnknownTypeClaimRejectedAtBothEndpoints() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        UUID userId = UUID.randomUUID();

        // Token with unknown 'type' claim
        String spoofedToken = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "spoofed.user@enterprise.io")
                .claim("role", "USER")
                .claim(JwtTokenProvider.CLAIM_TYPE, "super_admin_session")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        // 1. Rejected as API bearer token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + spoofedToken))
                .andExpect(status().isUnauthorized());

        // 2. Rejected as refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", spoofedToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Expired and invalidly signed tokens are rejected with HTTP 401")
    public void testExpiredAndInvalidlySignedTokensRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        UUID userId = UUID.randomUUID();

        // Expired access token
        String expiredAccessToken = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "expired@enterprise.io")
                .claim("role", "USER")
                .claim(JwtTokenProvider.CLAIM_TYPE, JwtTokenProvider.TYPE_ACCESS)
                .issuedAt(new Date(System.currentTimeMillis() - 20000))
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();

        // Expired refresh token
        String expiredRefreshToken = Jwts.builder()
                .subject(userId.toString())
                .claim(JwtTokenProvider.CLAIM_TYPE, JwtTokenProvider.TYPE_REFRESH)
                .issuedAt(new Date(System.currentTimeMillis() - 20000))
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();

        // Invalidly signed token (signed with a different key)
        SecretKey attackerKey = Keys.hmacShaKeyFor("different-attacker-secret-key-at-least-32-chars-long".getBytes(StandardCharsets.UTF_8));
        String forgedAccessToken = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "forged@enterprise.io")
                .claim("role", "USER")
                .claim(JwtTokenProvider.CLAIM_TYPE, JwtTokenProvider.TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(attackerKey)
                .compact();

        // 1. Expired access token rejected at API
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredAccessToken))
                .andExpect(status().isUnauthorized());

        // 2. Expired refresh token rejected at refresh
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", expiredRefreshToken))))
                .andExpect(status().isUnauthorized());

        // 3. Forged token rejected at API
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + forgedAccessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Complete password reset flow: request reset, receive code, update password, verify old password and previous sessions invalidated")
    public void testCompletePasswordResetFlowAndSessionInvalidation() throws Exception {
        String email = "reese.reset-" + UUID.randomUUID() + "@enterprise.io";
        String initialPassword = "InitialPassword123!";
        String newPassword = "BrandNewPassword456!";

        // 1. Register user
        RegisterRequest registerReq = new RegisterRequest("John Reese", email, initialPassword);
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode regJson = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String initialAccessToken = regJson.path("data").path("token").asText();
        String initialRefreshToken = regJson.path("data").path("refreshToken").asText();

        // 2. Verify initial access token works at /me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + initialAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        // 3. Request password reset
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If the email exists in our system, password reset instructions have been sent."));

        // Capture dispatched reset code from email service
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendPasswordResetEmail(eq(email), codeCaptor.capture(), any());
        String resetCode = codeCaptor.getValue();
        assertNotNull(resetCode);
        assertEquals(6, resetCode.length());

        // 4. Submit invalid code -> rejected with 400 Bad Request
        ResetPasswordRequest invalidReq = new ResetPasswordRequest(email, "000000", newPassword);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 5. Submit valid reset code and new password -> succeeds with 200 OK
        ResetPasswordRequest validResetReq = new ResetPasswordRequest(email, resetCode, newPassword);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 6. Old password fails login -> 401 Unauthorized
        LoginRequest oldLoginReq = new LoginRequest(email, initialPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLoginReq)))
                .andExpect(status().isUnauthorized());

        // 7. New password succeeds login -> 200 OK
        LoginRequest newLoginReq = new LoginRequest(email, newPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 8. SESSION INVALIDATION: Old access token is rejected at /me -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + initialAccessToken))
                .andExpect(status().isUnauthorized());

        // 9. SESSION INVALIDATION: Old refresh token is rejected at /refresh -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", initialRefreshToken))))
                .andExpect(status().isUnauthorized());

        // 10. Re-using the reset code fails -> 400 Bad Request (single-use)
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Non-existent account requesting password reset receives identical response (enumeration prevention)")
    public void testForgotPasswordNonExistentAccountReturnsIdenticalResponse() throws Exception {
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("nonexistent.user." + UUID.randomUUID() + "@enterprise.io");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If the email exists in our system, password reset instructions have been sent."));
    }

    @Test
    @DisplayName("When email service is unavailable, API informs client without falsely claiming delivery")
    public void testForgotPasswordWhenEmailServiceUnavailableInformsClient() throws Exception {
        org.mockito.Mockito.doReturn(false).when(emailService).isAvailable();
        try {
            ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("user." + UUID.randomUUID() + "@enterprise.io");
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(forgotReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password reset request received. Email delivery is currently unavailable or unconfigured."))
                    .andExpect(jsonPath("$.data").value("Email service unavailable"));
        } finally {
            org.mockito.Mockito.doReturn(true).when(emailService).isAvailable();
        }
    }
}
