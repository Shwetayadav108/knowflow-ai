package com.knowflow.ai.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

@Service
@Profile("prod")
public class ProductionEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ProductionEmailService.class);

    @Value("${knowflow.mail.smtp.host:}")
    private String smtpHost;

    @Value("${knowflow.mail.smtp.port:587}")
    private int smtpPort = 587;

    @Value("${knowflow.mail.smtp.username:}")
    private String smtpUsername;

    @Value("${knowflow.mail.smtp.password:}")
    private String smtpPassword;

    @Value("${knowflow.mail.smtp.from:no-reply@knowflow.ai}")
    private String fromEmail = "no-reply@knowflow.ai";

    @Value("${knowflow.mail.smtp.timeout-ms:5000}")
    private int timeoutMs = 5000;

    private JavaMailSender mailSender;

    public ProductionEmailService() {
        this.mailSender = null;
    }

    public ProductionEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public ProductionEmailService(String smtpHost, int smtpPort, String smtpUsername, String smtpPassword, String fromEmail, int timeoutMs) {
        this.mailSender = null;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.fromEmail = fromEmail;
        this.timeoutMs = timeoutMs;
    }

    public ProductionEmailService(JavaMailSender mailSender, String smtpHost, int smtpPort, String smtpUsername, String smtpPassword, String fromEmail, int timeoutMs) {
        this.mailSender = mailSender;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.fromEmail = fromEmail;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public boolean isAvailable() {
        return smtpHost != null && !smtpHost.trim().isEmpty();
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String resetCode, Instant expiresAt) {
        if (!isAvailable()) {
            log.warn("Production password reset email requested, but SMTP is not configured. Email suppressed safely.");
            return false;
        }

        if (toEmail == null || toEmail.isBlank() || resetCode == null || resetCode.isBlank()) {
            log.warn("Password reset email dispatch aborted due to missing recipient or code.");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail.trim());
            message.setSubject("KnowFlow AI Password Reset Verification Code");
            message.setText("Your KnowFlow AI password reset verification code is: " + resetCode + "\n\n"
                    + "This code will expire in 15 minutes.\n"
                    + "If you did not request a password reset, please ignore this email or contact security.");

            getMailSender().send(message);
            log.info("Dispatched password reset email via configured SMTP relay {}:{}.", smtpHost, smtpPort);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send password reset email via SMTP relay {}:{}: exception={}", smtpHost, smtpPort, ex.getClass().getSimpleName());
            return false;
        }
    }

    public synchronized JavaMailSender getMailSender() {
        if (this.mailSender == null) {
            this.mailSender = createJavaMailSender();
        }
        return this.mailSender;
    }

    public JavaMailSenderImpl createJavaMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtpHost);
        sender.setPort(smtpPort);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        boolean hasCredentials = smtpUsername != null && !smtpUsername.isBlank();
        if (hasCredentials) {
            sender.setUsername(smtpUsername);
            sender.setPassword(smtpPassword != null ? smtpPassword : "");
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");

        // Finite connection, read, and write timeouts (prevent hanging requests)
        String timeoutStr = String.valueOf(timeoutMs > 0 ? timeoutMs : 5000);
        props.put("mail.smtp.connectiontimeout", timeoutStr);
        props.put("mail.smtp.timeout", timeoutStr);
        props.put("mail.smtp.writetimeout", timeoutStr);

        // SMTP Authentication
        props.put("mail.smtp.auth", String.valueOf(hasCredentials));

        // Transport TLS/SSL configuration per port
        if (smtpPort == 465) {
            // Port 465: Implicit SSL/TLS enabled, do not attempt STARTTLS
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
        } else if (smtpPort == 587) {
            // Port 587: STARTTLS enabled, required, never plaintext downgrade
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.enable", "false");
        } else {
            // Baseline secure configuration for other ports
            props.put("mail.smtp.starttls.enable", "true");
        }

        return sender;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }
}
