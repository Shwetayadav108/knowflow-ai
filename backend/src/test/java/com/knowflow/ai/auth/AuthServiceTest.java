package com.knowflow.ai.auth;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.UnauthorizedException;
import com.knowflow.ai.common.exception.ValidationException;
import com.knowflow.ai.security.JwtTokenProvider;
import com.knowflow.ai.user.GlobalRole;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import com.knowflow.ai.workspace.Workspace;
import com.knowflow.ai.workspace.WorkspaceMembership;
import com.knowflow.ai.workspace.WorkspaceMembershipRepository;
import com.knowflow.ai.workspace.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private com.knowflow.ai.workspace.WorkspaceInvitationRepository invitationRepository;

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
    private com.knowflow.ai.email.EmailService emailService;

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
                "super-secure-production-ready-jwt-signing-key-minimum-256-bits-32bytes"
        );
    }

    @Test
    public void testRegisterNewUserSuccess() {
        RegisterRequest req = new RegisterRequest("Alice Smith", "alice@enterprise.com", "Password123!");

        when(userRepository.existsByEmailAndDeletedAtIsNull("alice@enterprise.com")).thenReturn(false);
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L); // First user -> SUPER_ADMIN
        when(passwordEncoder.encode(any())).thenReturn("hashed_password_secret");

        User savedUser = new User("alice@enterprise.com", "hashed_password_secret", "Alice Smith", GlobalRole.SUPER_ADMIN);
        savedUser.setId(UUID.randomUUID());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Workspace savedWorkspace = new Workspace("Alice Smith's Workspace", "workspace-12345", "Desc", savedUser.getId());
        savedWorkspace.setId(UUID.randomUUID());
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(savedWorkspace);

        when(tokenProvider.generateAccessToken(any(), any(), any(), anyInt())).thenReturn("mock_access_jwt");
        when(tokenProvider.generateRefreshToken(any(), anyInt())).thenReturn("mock_refresh_jwt");

        AuthResponse res = authService.register(req);

        assertNotNull(res);
        assertEquals("mock_access_jwt", res.getToken());
        assertEquals("alice@enterprise.com", res.getUser().getEmail());
        assertEquals(GlobalRole.SUPER_ADMIN, res.getUser().getGlobalRole());
        verify(userRepository).save(any(User.class));
        verify(workspaceRepository).save(any(Workspace.class));
        verify(membershipRepository).save(any(WorkspaceMembership.class));
    }

    @Test
    public void testRegisterDuplicateEmailThrowsException() {
        RegisterRequest req = new RegisterRequest("Bob Jones", "bob@enterprise.com", "Password123!");
        when(userRepository.existsByEmailAndDeletedAtIsNull("bob@enterprise.com")).thenReturn(true);

        assertThrows(ValidationException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    public void testRefreshTokenWithValidRefreshTokenSucceeds() {
        String validRefreshToken = "valid_refresh_token_string";
        UUID userId = UUID.randomUUID();
        User user = new User("carol@enterprise.com", "hashed_pw", "Carol White", GlobalRole.USER);
        user.setId(userId);

        when(tokenProvider.validateRefreshToken(validRefreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(validRefreshToken)).thenReturn(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(tokenProvider.getTokenVersion(validRefreshToken)).thenReturn(1);
        when(tokenProvider.generateAccessToken(any(), any(), any(), anyInt())).thenReturn("new_access_token");
        when(tokenProvider.generateRefreshToken(any(), anyInt())).thenReturn("new_refresh_token");

        AuthResponse res = authService.refreshToken(validRefreshToken);

        assertNotNull(res);
        assertEquals("new_access_token", res.getToken());
        assertEquals("new_refresh_token", res.getRefreshToken());
        assertEquals("carol@enterprise.com", res.getUser().getEmail());
        verify(tokenProvider, times(1)).validateRefreshToken(validRefreshToken);
    }

    @Test
    public void testRefreshTokenWithAccessTokenOrInvalidTokenThrowsUnauthorized() {
        String invalidToken = "not_a_refresh_token";
        when(tokenProvider.validateRefreshToken(invalidToken)).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                authService.refreshToken(invalidToken)
        );

        assertEquals("Invalid or expired refresh token.", ex.getMessage());
        verify(tokenProvider, times(1)).validateRefreshToken(invalidToken);
        verify(tokenProvider, never()).getUserIdFromToken(any());
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any());
    }
}
