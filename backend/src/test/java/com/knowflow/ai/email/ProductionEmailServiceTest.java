package com.knowflow.ai.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductionEmailServiceTest {

    @Mock
    private JavaMailSender mockMailSender;

    @Test
    @DisplayName("Port 587 configures STARTTLS enabled, required, and disables SSL")
    public void testPort587ConfiguresStartTlsRequired() {
        ProductionEmailService service = new ProductionEmailService(
                "smtp.mailgun.org", 587, "user@example.com", "secret123", "no-reply@knowflow.ai", 6000
        );

        JavaMailSenderImpl sender = service.createJavaMailSender();
        Properties props = sender.getJavaMailProperties();

        assertThat(sender.getHost()).isEqualTo("smtp.mailgun.org");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo("false");
    }

    @Test
    @DisplayName("Port 465 configures implicit SSL/TLS enabled and disables STARTTLS")
    public void testPort465ConfiguresImplicitSslTls() {
        ProductionEmailService service = new ProductionEmailService(
                "smtp.gmail.com", 465, "user@gmail.com", "app-password", "no-reply@knowflow.ai", 4000
        );

        JavaMailSenderImpl sender = service.createJavaMailSender();
        Properties props = sender.getJavaMailProperties();

        assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
        assertThat(sender.getPort()).isEqualTo(465);
        assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("false");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("false");
    }

    @Test
    @DisplayName("Authentication is enabled when username and password are provided")
    public void testAuthenticationEnabledWhenCredentialsSupplied() {
        ProductionEmailService service = new ProductionEmailService(
                "smtp.example.com", 587, "smtp-user", "smtp-pass", "no-reply@knowflow.ai", 5000
        );

        JavaMailSenderImpl sender = service.createJavaMailSender();
        Properties props = sender.getJavaMailProperties();

        assertThat(sender.getUsername()).isEqualTo("smtp-user");
        assertThat(sender.getPassword()).isEqualTo("smtp-pass");
        assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    @DisplayName("Authentication is disabled when username is null or blank")
    public void testAuthenticationDisabledWhenCredentialsOmitted() {
        ProductionEmailService serviceWithNull = new ProductionEmailService(
                "smtp.internal.relay", 25, null, null, "no-reply@knowflow.ai", 5000
        );
        JavaMailSenderImpl senderNull = serviceWithNull.createJavaMailSender();
        assertThat(senderNull.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");

        ProductionEmailService serviceWithBlank = new ProductionEmailService(
                "smtp.internal.relay", 25, "   ", "", "no-reply@knowflow.ai", 5000
        );
        JavaMailSenderImpl senderBlank = serviceWithBlank.createJavaMailSender();
        assertThat(senderBlank.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
    }

    @Test
    @DisplayName("Finite connection, read, and write timeouts are configured")
    public void testFiniteTimeoutsConfigured() {
        int expectedTimeout = 7500;
        ProductionEmailService service = new ProductionEmailService(
                "smtp.example.com", 587, null, null, "no-reply@knowflow.ai", expectedTimeout
        );

        JavaMailSenderImpl sender = service.createJavaMailSender();
        Properties props = sender.getJavaMailProperties();

        assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo(String.valueOf(expectedTimeout));
        assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo(String.valueOf(expectedTimeout));
        assertThat(props.getProperty("mail.smtp.writetimeout")).isEqualTo(String.valueOf(expectedTimeout));
    }

    @Test
    @DisplayName("isAvailable returns true only when host is non-null and non-blank")
    public void testIsAvailableWithVariousHostValues() {
        ProductionEmailService nullHost = new ProductionEmailService(null, 587, null, null, "no-reply@knowflow.ai", 5000);
        assertThat(nullHost.isAvailable()).isFalse();

        ProductionEmailService emptyHost = new ProductionEmailService("", 587, null, null, "no-reply@knowflow.ai", 5000);
        assertThat(emptyHost.isAvailable()).isFalse();

        ProductionEmailService whitespaceHost = new ProductionEmailService("   ", 587, null, null, "no-reply@knowflow.ai", 5000);
        assertThat(whitespaceHost.isAvailable()).isFalse();

        ProductionEmailService validHost = new ProductionEmailService("smtp.sendgrid.net", 587, null, null, "no-reply@knowflow.ai", 5000);
        assertThat(validHost.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("sendPasswordResetEmail safely suppresses email and returns false when SMTP unconfigured")
    public void testSendWhenUnconfiguredReturnsFalse() {
        ProductionEmailService service = new ProductionEmailService(mockMailSender, null, 587, null, null, "no-reply@knowflow.ai", 5000);

        boolean result = service.sendPasswordResetEmail("user@example.com", "123456", Instant.now());

        assertThat(result).isFalse();
        verifyNoInteractions(mockMailSender);
    }

    @Test
    @DisplayName("sendPasswordResetEmail returns false on invalid recipient or code")
    public void testSendWithInvalidRecipientOrCodeReturnsFalse() {
        ProductionEmailService service = new ProductionEmailService(mockMailSender, "smtp.example.com", 587, null, null, "no-reply@knowflow.ai", 5000);

        assertThat(service.sendPasswordResetEmail(null, "123456", Instant.now())).isFalse();
        assertThat(service.sendPasswordResetEmail("   ", "123456", Instant.now())).isFalse();
        assertThat(service.sendPasswordResetEmail("user@example.com", null, Instant.now())).isFalse();
        assertThat(service.sendPasswordResetEmail("user@example.com", "", Instant.now())).isFalse();
        verifyNoInteractions(mockMailSender);
    }

    @Test
    @DisplayName("sendPasswordResetEmail dispatches message with correct fields on success")
    public void testSendSuccessDispatchesMessage() {
        ProductionEmailService service = new ProductionEmailService(
                mockMailSender, "smtp.example.com", 587, "user", "pass", "no-reply@knowflow.ai", 5000
        );

        boolean result = service.sendPasswordResetEmail("recipient@example.com", "847291", Instant.now());

        assertThat(result).isTrue();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("no-reply@knowflow.ai");
        assertThat(sent.getTo()).containsExactly("recipient@example.com");
        assertThat(sent.getSubject()).isEqualTo("KnowFlow AI Password Reset Verification Code");
        assertThat(sent.getText()).contains("847291");
        assertThat(sent.getText()).contains("15 minutes");
    }

    @Test
    @DisplayName("sendPasswordResetEmail safely catches MailException and returns false without propagating")
    public void testSendCatchesMailExceptionSafely() {
        ProductionEmailService service = new ProductionEmailService(
                mockMailSender, "smtp.example.com", 587, "user", "pass", "no-reply@knowflow.ai", 5000
        );

        doThrow(new MailAuthenticationException("Authentication failed for user"))
                .when(mockMailSender).send(any(SimpleMailMessage.class));

        boolean result = service.sendPasswordResetEmail("recipient@example.com", "847291", Instant.now());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("sendPasswordResetEmail safely catches MailSendException (connection/transport errors)")
    public void testSendCatchesMailSendExceptionSafely() {
        ProductionEmailService service = new ProductionEmailService(
                mockMailSender, "smtp.example.com", 587, "user", "pass", "no-reply@knowflow.ai", 5000
        );

        doThrow(new MailSendException("Connection timed out to SMTP server"))
                .when(mockMailSender).send(any(SimpleMailMessage.class));

        boolean result = service.sendPasswordResetEmail("recipient@example.com", "847291", Instant.now());

        assertThat(result).isFalse();
    }
}
