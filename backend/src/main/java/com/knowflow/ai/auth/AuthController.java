package com.knowflow.ai.auth;

import com.knowflow.ai.common.ApiResponse;
import com.knowflow.ai.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Account registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        UserDto user = authService.getCurrentUser(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PatchMapping("/onboarding-complete")
    public ResponseEntity<ApiResponse<UserDto>> completeOnboarding(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        UserDto user = authService.completeOnboarding(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Onboarding marked as completed", user));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        boolean dispatched = authService.forgotPassword(request.getEmail());
        if (!dispatched) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Password reset request received. Email delivery is currently unavailable or unconfigured.",
                    "Email service unavailable"
            ));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "If the email exists in our system, password reset instructions have been sent.",
                "Reset email dispatched"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Password has been reset successfully. You may now log in with your new password.",
                "Password reset completed"
        ));
    }
}
