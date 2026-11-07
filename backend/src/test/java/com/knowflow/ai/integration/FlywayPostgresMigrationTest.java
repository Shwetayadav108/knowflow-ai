package com.knowflow.ai.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
public class FlywayPostgresMigrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("knowflow_migration_test")
            .withUsername("test")
            .withPassword("test");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void setup() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping live PostgreSQL migration test.");

        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("Verify Flyway migrations V1 -> V2 -> V3 -> V4, default token_version on existing users, constraints and indexes")
    public void testFullPostgresMigrationLifecycle() {
        // 1. Run migrations up to V3 in a dedicated test schema
        String schemaName = "migration_verify_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbcTemplate.execute("CREATE SCHEMA " + schemaName);

        Flyway flywayV3 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration")
                .target("3")
                .load();

        flywayV3.migrate();

        // 2. Insert existing user into pre-V4 schema (before token_version column exists)
        UUID preExistingUserId = UUID.randomUUID();
        String preExistingEmail = "legacy.user." + UUID.randomUUID() + "@enterprise.io";
        jdbcTemplate.update(
                "INSERT INTO " + schemaName + ".users (id, email, password_hash, full_name) VALUES (?, ?, 'hashed_pw', 'Legacy User')",
                preExistingUserId, preExistingEmail
        );

        // 3. Migrate from V3 to V4
        Flyway flywayV4 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration")
                .target("4")
                .load();

        var migrationResult = flywayV4.migrate();
        assertTrue(migrationResult.success);
        assertEquals("4", migrationResult.targetSchemaVersion);

        // 4. Verify existing user received default token_version = 1
        Map<String, Object> userRow = jdbcTemplate.queryForMap(
                "SELECT id, email, token_version FROM " + schemaName + ".users WHERE id = ?",
                preExistingUserId
        );
        assertEquals(1, ((Number) userRow.get("token_version")).intValue());

        // 5. Verify token_version column metadata in information_schema
        Map<String, Object> colMeta = jdbcTemplate.queryForMap(
                "SELECT column_name, data_type, is_nullable, column_default " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = ? AND table_name = 'users' AND column_name = 'token_version'",
                schemaName
        );
        assertEquals("integer", colMeta.get("data_type"));
        assertEquals("NO", colMeta.get("is_nullable"));
        assertTrue(colMeta.get("column_default").toString().contains("1"));

        // 6. Verify password_reset_tokens table exists and can hold reset records
        UUID tokenId = UUID.randomUUID();
        String sampleHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES));

        jdbcTemplate.update(
                "INSERT INTO " + schemaName + ".password_reset_tokens " +
                        "(id, user_id, email, token_hash, status, failed_attempts, expires_at) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', 0, ?)",
                tokenId, preExistingUserId, preExistingEmail, sampleHash, expiresAt
        );

        int tokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + ".password_reset_tokens WHERE user_id = ?",
                Integer.class, preExistingUserId
        );
        assertEquals(1, tokenCount);

        // 7. Verify foreign key ON DELETE CASCADE
        jdbcTemplate.update("DELETE FROM " + schemaName + ".users WHERE id = ?", preExistingUserId);
        int remainingTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + ".password_reset_tokens WHERE id = ?",
                Integer.class, tokenId
        );
        assertEquals(0, remainingTokens, "Deleting user must cascade-delete password_reset_tokens");

        // 8. Verify all required indexes exist in PostgreSQL
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = 'password_reset_tokens'",
                String.class, schemaName
        );
        assertTrue(indexes.contains("idx_pwd_reset_token_hash"), "Missing idx_pwd_reset_token_hash");
        assertTrue(indexes.contains("idx_pwd_reset_user_id"), "Missing idx_pwd_reset_user_id");
        assertTrue(indexes.contains("idx_pwd_reset_email"), "Missing idx_pwd_reset_email");
        assertTrue(indexes.contains("idx_pwd_reset_status"), "Missing idx_pwd_reset_status");
    }
}
