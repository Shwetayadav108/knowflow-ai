package com.knowflow.ai.email;

import java.time.Instant;

public interface EmailService {

    boolean isAvailable();

    boolean sendPasswordResetEmail(String toEmail, String resetCode, Instant expiresAt);
}
