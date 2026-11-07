package com.knowflow.ai.auth;

import com.knowflow.ai.common.BaseEntity;
import com.knowflow.ai.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PasswordResetTokenStatus status = PasswordResetTokenStatus.PENDING;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public PasswordResetToken() {
    }

    public PasswordResetToken(User user, String email, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.status = PasswordResetTokenStatus.PENDING;
        this.failedAttempts = 0;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == PasswordResetTokenStatus.PENDING && !isExpired();
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public PasswordResetTokenStatus getStatus() {
        return status;
    }

    public void setStatus(PasswordResetTokenStatus status) {
        this.status = status;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
