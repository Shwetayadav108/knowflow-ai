package com.knowflow.ai.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("!prod")
public class ConsoleLoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLoggingEmailService.class);

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String resetCode, Instant expiresAt) {
        String banner = "\n" +
                "================================================================================\n" +
                "[LOCAL DEV EMAIL] Password Reset Verification Code Dispatched\n" +
                "To: " + toEmail + "\n" +
                "Reset Code: " + resetCode + "\n" +
                "Expires At: " + expiresAt + " (Valid for 15 minutes)\n" +
                "(Logged locally for development/testing only. No external email was dispatched.)\n" +
                "================================================================================";
        log.info("{}", banner);
        return true;
    }
}
