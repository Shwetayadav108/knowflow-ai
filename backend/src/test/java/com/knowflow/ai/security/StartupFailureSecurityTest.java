package com.knowflow.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

public class StartupFailureSecurityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PropertySourcesPlaceholderConfigurer.class)
            .withUserConfiguration(JwtTokenProvider.class);

    @Test
    @DisplayName("Startup fails when knowflow.security.jwt.secret property is missing")
    public void testStartupFailsWhenJwtSecretIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Could not resolve placeholder 'knowflow.security.jwt.secret'");
        });
    }

    @Test
    @DisplayName("Startup fails with clear security error when JWT_SECRET is empty string")
    public void testStartupFailsWhenJwtSecretIsEmpty() {
        contextRunner
                .withPropertyValues("knowflow.security.jwt.secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("JWT configuration error: JWT_SECRET environment variable is missing or empty");
                });
    }

    @Test
    @DisplayName("Startup fails with clear security error when JWT_SECRET is too short (< 256 bits)")
    public void testStartupFailsWhenJwtSecretIsTooShort() {
        contextRunner
                .withPropertyValues("knowflow.security.jwt.secret=too-short-weak-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("does not meet the minimum security requirements for HMAC-SHA algorithms");
                });
    }

    @Test
    @DisplayName("Startup succeeds when JWT_SECRET meets 256-bit requirement")
    public void testStartupSucceedsWithValidSecret() {
        contextRunner
                .withPropertyValues("knowflow.security.jwt.secret=valid-secret-key-that-has-at-least-32-bytes-length-minimum-256-bits")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtTokenProvider.class);
                });
    }

    @Test
    @DisplayName("Production profile activates ProductionEmailService and strictly prevents ConsoleLoggingEmailService")
    public void testProductionProfileNeverActivatesConsoleLoggingEmailService() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(
                        com.knowflow.ai.email.ConsoleLoggingEmailService.class,
                        com.knowflow.ai.email.ProductionEmailService.class
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(com.knowflow.ai.email.EmailService.class);
                    assertThat(context.getBean(com.knowflow.ai.email.EmailService.class))
                            .isInstanceOf(com.knowflow.ai.email.ProductionEmailService.class);
                    assertThat(context.containsBean("consoleLoggingEmailService")).isFalse();
                });
    }

    @Test
    @DisplayName("Local profile activates ConsoleLoggingEmailService")
    public void testLocalProfileActivatesConsoleLoggingEmailService() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=local")
                .withUserConfiguration(
                        com.knowflow.ai.email.ConsoleLoggingEmailService.class,
                        com.knowflow.ai.email.ProductionEmailService.class
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(com.knowflow.ai.email.EmailService.class);
                    assertThat(context.getBean(com.knowflow.ai.email.EmailService.class))
                            .isInstanceOf(com.knowflow.ai.email.ConsoleLoggingEmailService.class);
                });
    }
}
