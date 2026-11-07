package com.knowflow.ai.auth;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import com.knowflow.ai.common.exception.UnauthorizedException;
import com.knowflow.ai.common.exception.ValidationException;
import com.knowflow.ai.security.JwtTokenProvider;
import com.knowflow.ai.user.GlobalRole;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import com.knowflow.ai.user.UserStatus;
import com.knowflow.ai.workspace.*;
import com.knowflow.ai.email.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final String resetSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AuthService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            WorkspaceInvitationRepository invitationRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            AuthenticationManager authenticationManager,
            AuditService auditService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailService emailService,
            @Value("${knowflow.security.reset.secret:knowflow-default-reset-hmac-secret-at-least-32-chars-long}") String resetSecret
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        if (resetSecret == null || resetSecret.trim().isEmpty() || resetSecret.trim().length() < 32) {
            throw new IllegalStateException(
                "Password reset configuration error: RESET_TOKEN_SECRET does not meet the minimum security requirements "
                + "for HMAC-SHA256 algorithms. Must be at least 256 bits (32 characters)."
            );
        }
        this.resetSecret = resetSecret;
    }

    public AuthService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            WorkspaceInvitationRepository invitationRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            AuthenticationManager authenticationManager,
            AuditService auditService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailService emailService
    ) {
        this(userRepository, workspaceRepository, membershipRepository, invitationRepository,
                passwordEncoder, tokenProvider, authenticationManager, auditService,
                passwordResetTokenRepository, emailService,
                "knowflow-default-reset-hmac-secret-at-least-32-chars-long");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new ValidationException("An account with this email address already exists.");
        }

        // Validate invite token if present
        WorkspaceInvitation invitation = null;
        if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
            invitation = invitationRepository.findByToken(request.getInviteToken().trim())
                    .orElseThrow(() -> new ValidationException("Invalid or non-existent invitation token."));
            if (!invitation.isPending()) {
                throw new ValidationException("This invitation is no longer active or has expired.");
            }
        }

        // First user bootstrap mechanism: only the first registered user becomes SUPER_ADMIN
        long count = userRepository.countByDeletedAtIsNull();
        GlobalRole role = (count == 0) ? GlobalRole.SUPER_ADMIN : GlobalRole.USER;

        User user = new User(
                email,
                passwordEncoder.encode(request.getPassword()),
                request.getFullName().trim(),
                role
        );
        user = userRepository.save(user);

        // Provision personal default workspace
        String slug = "workspace-" + UUID.randomUUID().toString().substring(0, 8);
        Workspace defaultWorkspace = new Workspace(
                user.getFullName() + "'s Workspace",
                slug,
                "Default workspace for " + user.getFullName(),
                user.getId()
        );
        defaultWorkspace = workspaceRepository.save(defaultWorkspace);

        WorkspaceMembership membership = new WorkspaceMembership(
                defaultWorkspace,
                user,
                WorkspaceRole.WORKSPACE_ADMIN
        );
        membershipRepository.save(membership);

        // If registered with an invitation token, attach to the invited workspace with the designated role
        if (invitation != null) {
            Optional<Workspace> invitedWsOpt = workspaceRepository.findByIdAndDeletedAtIsNull(invitation.getWorkspaceId());
            if (invitedWsOpt.isPresent()) {
                Workspace invitedWs = invitedWsOpt.get();
                WorkspaceMembership invitedMembership = new WorkspaceMembership(
                        invitedWs,
                        user,
                        invitation.getRole()
                );
                membershipRepository.save(invitedMembership);
                invitation.setStatus(InvitationStatus.ACCEPTED);
                invitationRepository.save(invitation);

                auditService.logAsync(invitedWs.getId(), user.getId(), "INVITATION_ACCEPTED", "WORKSPACE_INVITATION",
                        invitation.getId().toString(), user.getEmail() + " registered and joined workspace as " + invitation.getRole(), null, "SUCCESS");
            }
        }

        String token = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getGlobalRole().name(), user.getTokenVersion());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getTokenVersion());

        auditService.logAsync(defaultWorkspace.getId(), user.getId(), "USER_REGISTERED", "USER", user.getId().toString(), "Account created successfully", null, "SUCCESS");

        return new AuthResponse(token, refreshToken, UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            auditService.logAsync(null, null, "LOGIN_FAILED", "USER", email, "Invalid credentials attempt for " + email, null, "FAILURE");
            throw new UnauthorizedException("Invalid email or password.");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findByIdAndDeletedAtIsNull(userDetails.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found or suspended."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Your account is not active. Status: " + user.getStatus());
        }

        String token = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getGlobalRole().name(), user.getTokenVersion());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getTokenVersion());

        auditService.logAsync(null, user.getId(), "USER_LOGIN", "USER", user.getId().toString(), "Successful login", null, "SUCCESS");

        return new AuthResponse(token, refreshToken, UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }

        UUID userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found."));

        Integer version = tokenProvider.getTokenVersion(refreshToken);
        if (version == null || version != user.getTokenVersion()) {
            throw new UnauthorizedException("Session has been invalidated. Please log in again.");
        }

        String newToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getGlobalRole().name(), user.getTokenVersion());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getTokenVersion());

        return new AuthResponse(newToken, newRefreshToken, UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found."));
        return UserDto.from(user);
    }

    @Transactional
    public UserDto completeOnboarding(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setOnboardingCompleted(true);
        user = userRepository.save(user);

        auditService.logAsync(null, userId, "ONBOARDING_COMPLETED", "USER", userId.toString(), "User completed initial product walkthrough", null, "SUCCESS");
        return UserDto.from(user);
    }

    @Transactional
    public boolean forgotPassword(String rawEmail) {
        if (!emailService.isAvailable()) {
            return false;
        }
        if (rawEmail == null || rawEmail.isBlank()) {
            return true;
        }
        String email = rawEmail.toLowerCase().trim();
        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(email);

        if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.ACTIVE) {
            // Constant-time equalization against timing attacks
            computeHmacSha256("000000", resetSecret);
            return true;
        }

        User user = userOpt.get();

        // 1. Revoke any previous pending codes
        passwordResetTokenRepository.revokeAllPendingByUserId(user.getId());

        // 2. Generate 6-digit cryptographically secure code
        int codeInt = secureRandom.nextInt(1_000_000);
        String rawCode = String.format("%06d", codeInt);

        // 3. Store only HMAC-SHA256 hash using separate secret
        String tokenHash = computeHmacSha256(rawCode, resetSecret);

        // 4. Expires after 15 minutes
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        PasswordResetToken token = new PasswordResetToken(user, email, tokenHash, expiresAt);
        passwordResetTokenRepository.save(token);

        auditService.logAsync(null, user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString(),
                "Password reset code dispatched", null, "SUCCESS");

        // 5. Send email (console in dev/test, safe in prod)
        emailService.sendPasswordResetEmail(email, rawCode, expiresAt);
        return true;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request == null || request.getEmail() == null || request.getCode() == null || request.getNewPassword() == null) {
            throw new ValidationException("Invalid or expired password reset code.");
        }

        String email = request.getEmail().toLowerCase().trim();
        String rawCode = request.getCode().trim();
        String newPassword = request.getNewPassword();

        validatePasswordComplexity(newPassword);

        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ValidationException("Invalid or expired password reset code."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ValidationException("Invalid or expired password reset code.");
        }

        // Concurrency protection: acquire pessimistic write lock on pending reset token
        List<PasswordResetToken> pendingTokens = passwordResetTokenRepository
                .findByUserIdAndStatusForUpdate(user.getId(), PasswordResetTokenStatus.PENDING);

        if (pendingTokens.isEmpty()) {
            throw new ValidationException("Invalid or expired password reset code.");
        }

        PasswordResetToken resetToken = pendingTokens.get(0);

        if (resetToken.getStatus() != PasswordResetTokenStatus.PENDING || resetToken.isExpired()) {
            if (resetToken.getStatus() == PasswordResetTokenStatus.PENDING && resetToken.isExpired()) {
                resetToken.setStatus(PasswordResetTokenStatus.EXPIRED);
                passwordResetTokenRepository.save(resetToken);
            }
            throw new ValidationException("Invalid or expired password reset code.");
        }

        if (resetToken.getFailedAttempts() >= 5) {
            resetToken.setStatus(PasswordResetTokenStatus.REVOKED);
            passwordResetTokenRepository.save(resetToken);
            throw new ValidationException("Too many failed attempts. Please request a new password reset code.");
        }

        String expectedHash = resetToken.getTokenHash();
        String computedHash = computeHmacSha256(rawCode, resetSecret);

        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                computedHash.getBytes(StandardCharsets.UTF_8))) {

            int attempts = resetToken.getFailedAttempts() + 1;
            resetToken.setFailedAttempts(attempts);
            if (attempts >= 5) {
                resetToken.setStatus(PasswordResetTokenStatus.REVOKED);
            }
            passwordResetTokenRepository.save(resetToken);

            auditService.logAsync(null, user.getId(), "PASSWORD_RESET_FAILED", "USER", user.getId().toString(),
                    "Invalid reset code attempt (attempt " + attempts + "/5)", null, "FAILURE");

            if (attempts >= 5) {
                throw new ValidationException("Too many failed attempts. Please request a new password reset code.");
            } else {
                throw new ValidationException("Invalid or expired password reset code.");
            }
        }

        // Successfully verified: consume token (single-use)
        resetToken.setStatus(PasswordResetTokenStatus.USED);
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        // Update password with BCrypt
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        // Atomic session invalidation: increment token_version
        user.incrementTokenVersion();
        userRepository.save(user);

        auditService.logAsync(null, user.getId(), "PASSWORD_RESET_COMPLETED", "USER", user.getId().toString(),
                "Password reset successfully completed and session token version incremented", null, "SUCCESS");
    }

    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long.");
        }
        boolean hasUpper = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasUpper || !hasDigit) {
            throw new ValidationException("Password must contain at least one uppercase letter and one number.");
        }
    }
}
