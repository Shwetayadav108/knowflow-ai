package com.knowflow.ai.auth;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.ValidationException;
import com.knowflow.ai.email.EmailService;
import com.knowflow.ai.security.JwtTokenProvider;
import com.knowflow.ai.user.GlobalRole;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import com.knowflow.ai.workspace.WorkspaceInvitationRepository;
import com.knowflow.ai.workspace.WorkspaceMembershipRepository;
import com.knowflow.ai.workspace.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PasswordResetTest {

    private static final String RESET_SECRET = "test-reset-secret-for-hmac-sha256-calculation-at-least-32-bytes";

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private WorkspaceInvitationRepository invitationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    public void setup() {
        authService = new AuthService(
                userRepository,
                workspaceRepository,
                membershipRepository,
                invitationRepository,
                passwordEncoder,
                tokenProvider,
                authenticationManager,
                auditService,
                passwordResetTokenRepository,
                emailService,
                RESET_SECRET
        );
    }

    private String computeHmac(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(RESET_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Forgot password for active user revokes older tokens, stores HMAC hash, and sends email")
    public void testForgotPasswordActiveUser() {
        String email = "sarah.connor@enterprise.io";
        User user = new User(email, "old_encoded_pw", "Sarah Connor", GlobalRole.USER);
        user.setId(UUID.randomUUID());

        when(emailService.isAvailable()).thenReturn(true);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));

        boolean result = authService.forgotPassword(email);
        assertTrue(result);

        verify(passwordResetTokenRepository, times(1)).revokeAllPendingByUserId(user.getId());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository, times(1)).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertNotNull(savedToken);
        assertEquals(user, savedToken.getUser());
        assertEquals(email, savedToken.getEmail());
        assertEquals(PasswordResetTokenStatus.PENDING, savedToken.getStatus());
        assertEquals(0, savedToken.getFailedAttempts());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));
        assertNotNull(savedToken.getTokenHash());
        assertEquals(64, savedToken.getTokenHash().length());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendPasswordResetEmail(eq(email), codeCaptor.capture(), any());
        String dispatchedRawCode = codeCaptor.getValue();
        assertEquals(6, dispatchedRawCode.length());
        assertTrue(dispatchedRawCode.matches("\\d{6}"));

        assertEquals(computeHmac(dispatchedRawCode), savedToken.getTokenHash());
    }

    @Test
    @DisplayName("Forgot password when email service is unavailable returns false without modifying database")
    public void testForgotPasswordWhenEmailServiceUnavailableReturnsFalse() {
        String email = "sarah.connor@enterprise.io";
        when(emailService.isAvailable()).thenReturn(false);

        boolean result = authService.forgotPassword(email);
        assertFalse(result);

        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Forgot password for nonexistent user returns true without creating tokens or sending email")
    public void testForgotPasswordNonExistentUser() {
        String email = "ghost@enterprise.io";
        when(emailService.isAvailable()).thenReturn(true);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());

        boolean result = authService.forgotPassword(email);
        assertTrue(result);

        verify(passwordResetTokenRepository, never()).revokeAllPendingByUserId(any());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Reset password with valid code successfully updates password hash, increments token_version, and marks token USED")
    public void testResetPasswordSuccess() {
        String email = "sarah.connor@enterprise.io";
        String rawCode = "123456";
        String newPassword = "NewSecurePassword123!";
        String tokenHash = computeHmac(rawCode);

        User user = new User(email, "old_encoded_pw", "Sarah Connor", GlobalRole.USER);
        user.setId(UUID.randomUUID());
        assertEquals(1, user.getTokenVersion());

        PasswordResetToken resetToken = new PasswordResetToken(user, email, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));

        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserIdAndStatusForUpdate(user.getId(), PasswordResetTokenStatus.PENDING))
                .thenReturn(List.of(resetToken));
        when(passwordEncoder.encode(newPassword)).thenReturn("new_bcrypt_hash");

        ResetPasswordRequest req = new ResetPasswordRequest(email, rawCode, newPassword);
        authService.resetPassword(req);

        assertEquals(PasswordResetTokenStatus.USED, resetToken.getStatus());
        assertNotNull(resetToken.getUsedAt());
        verify(passwordResetTokenRepository, times(1)).save(resetToken);

        assertEquals("new_bcrypt_hash", user.getPasswordHash());
        assertEquals(2, user.getTokenVersion());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("Reset password with expired code fails validation and updates status to EXPIRED")
    public void testResetPasswordExpiredToken() {
        String email = "sarah.connor@enterprise.io";
        String rawCode = "123456";
        String tokenHash = computeHmac(rawCode);

        User user = new User(email, "old_encoded_pw", "Sarah Connor", GlobalRole.USER);
        user.setId(UUID.randomUUID());

        // Expired 5 minutes ago
        PasswordResetToken resetToken = new PasswordResetToken(user, email, tokenHash, Instant.now().minus(5, ChronoUnit.MINUTES));

        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserIdAndStatusForUpdate(user.getId(), PasswordResetTokenStatus.PENDING))
                .thenReturn(List.of(resetToken));

        ResetPasswordRequest req = new ResetPasswordRequest(email, rawCode, "NewPassword123!");
        ValidationException ex = assertThrows(ValidationException.class, () -> authService.resetPassword(req));

        assertEquals("Invalid or expired password reset code.", ex.getMessage());
        assertEquals(PasswordResetTokenStatus.EXPIRED, resetToken.getStatus());
        verify(passwordResetTokenRepository, times(1)).save(resetToken);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with wrong code increments failed attempts")
    public void testResetPasswordWrongCodeIncrementsAttempts() {
        String email = "sarah.connor@enterprise.io";
        String correctCode = "123456";
        String wrongCode = "654321";
        String tokenHash = computeHmac(correctCode);

        User user = new User(email, "old_encoded_pw", "Sarah Connor", GlobalRole.USER);
        user.setId(UUID.randomUUID());

        PasswordResetToken resetToken = new PasswordResetToken(user, email, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        assertEquals(0, resetToken.getFailedAttempts());

        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserIdAndStatusForUpdate(user.getId(), PasswordResetTokenStatus.PENDING))
                .thenReturn(List.of(resetToken));

        ResetPasswordRequest req = new ResetPasswordRequest(email, wrongCode, "NewPassword123!");
        ValidationException ex = assertThrows(ValidationException.class, () -> authService.resetPassword(req));

        assertEquals("Invalid or expired password reset code.", ex.getMessage());
        assertEquals(1, resetToken.getFailedAttempts());
        assertEquals(PasswordResetTokenStatus.PENDING, resetToken.getStatus());
        verify(passwordResetTokenRepository, times(1)).save(resetToken);
    }

    @Test
    @DisplayName("Reset password locks out (status REVOKED) after 5 failed attempts")
    public void testResetPasswordLockoutAfterFiveAttempts() {
        String email = "sarah.connor@enterprise.io";
        String correctCode = "123456";
        String wrongCode = "999999";
        String tokenHash = computeHmac(correctCode);

        User user = new User(email, "old_encoded_pw", "Sarah Connor", GlobalRole.USER);
        user.setId(UUID.randomUUID());

        PasswordResetToken resetToken = new PasswordResetToken(user, email, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        resetToken.setFailedAttempts(4);

        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserIdAndStatusForUpdate(user.getId(), PasswordResetTokenStatus.PENDING))
                .thenReturn(List.of(resetToken));

        ResetPasswordRequest req = new ResetPasswordRequest(email, wrongCode, "NewPassword123!");
        ValidationException ex = assertThrows(ValidationException.class, () -> authService.resetPassword(req));

        assertTrue(ex.getMessage().contains("Too many failed attempts"));
        assertEquals(5, resetToken.getFailedAttempts());
        assertEquals(PasswordResetTokenStatus.REVOKED, resetToken.getStatus());
        verify(passwordResetTokenRepository, times(1)).save(resetToken);
    }

    @Test
    @DisplayName("Reset password with weak password fails complexity check before database mutation")
    public void testResetPasswordWeakPassword() {
        ResetPasswordRequest reqNoDigit = new ResetPasswordRequest("user@enterprise.io", "123456", "PasswordOnly");
        ValidationException ex1 = assertThrows(ValidationException.class, () -> authService.resetPassword(reqNoDigit));
        assertTrue(ex1.getMessage().contains("Password must contain at least one uppercase letter and one number"));

        ResetPasswordRequest reqShort = new ResetPasswordRequest("user@enterprise.io", "123456", "Pass1");
        ValidationException ex2 = assertThrows(ValidationException.class, () -> authService.resetPassword(reqShort));
        assertTrue(ex2.getMessage().contains("at least 8 characters"));

        verify(userRepository, never()).save(any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("AuthService constructor validates RESET_TOKEN_SECRET length and presence without leaking secret")
    public void testResetSecretValidation() {
        String shortSecret = "too-short-secret";
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                new AuthService(
                        userRepository, workspaceRepository, membershipRepository, invitationRepository,
                        passwordEncoder, tokenProvider, authenticationManager, auditService,
                        passwordResetTokenRepository, emailService, shortSecret
                )
        );
        assertTrue(ex1.getMessage().contains("RESET_TOKEN_SECRET does not meet the minimum security requirements"));
        assertFalse(ex1.getMessage().contains(shortSecret));

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                new AuthService(
                        userRepository, workspaceRepository, membershipRepository, invitationRepository,
                        passwordEncoder, tokenProvider, authenticationManager, auditService,
                        passwordResetTokenRepository, emailService, null
                )
        );
        assertTrue(ex2.getMessage().contains("RESET_TOKEN_SECRET does not meet the minimum security requirements"));
    }
}
