package com.knowflow.ai.integration;

import com.knowflow.ai.document.DocumentChunk;
import com.knowflow.ai.vector.PgVectorSearchService;
import com.knowflow.ai.vector.ScoredChunk;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
public class PgVectorIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("knowflow_test")
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbcTemplate;
    private static PgVectorSearchService pgVectorSearchService;

    private static String create768DimensionVectorJson(double leadingVal) {
        StringBuilder sb = new StringBuilder("[");
        sb.append(leadingVal);
        for (int i = 1; i < 768; i++) {
            sb.append(",0.0");
        }
        sb.append("]");
        return sb.toString();
    }

    private static List<Double> create768DimensionVectorList(double leadingVal) {
        List<Double> list = new ArrayList<>(768);
        list.add(leadingVal);
        for (int i = 1; i < 768; i++) {
            list.add(0.0);
        }
        return list;
    }

    @BeforeAll
    public static void setup() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping live PostgreSQL pgvector container tests.");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        // Run full Flyway migrations: V1, V2, and V3
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        pgVectorSearchService = new PgVectorSearchService(jdbcTemplate);
        ReflectionTestUtils.setField(pgVectorSearchService, "vectorWeight", 0.70);
        ReflectionTestUtils.setField(pgVectorSearchService, "keywordWeight", 0.30);
    }

    private UUID createWorkspaceAndDocument() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name) VALUES (?, ?, 'hash', 'Test User') ON CONFLICT DO NOTHING",
                userId, "user-" + userId + "@enterprise.io"
        );

        jdbcTemplate.update(
                "INSERT INTO workspaces (id, name, slug, created_by) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                wsId, "Test Workspace", "slug-" + wsId, userId
        );

        jdbcTemplate.update(
                "INSERT INTO documents (id, workspace_id, title, original_filename, file_type, file_size_bytes, storage_path, mime_type, uploaded_by) " +
                "VALUES (?, ?, 'Test Doc', 'test.txt', 'TXT', 100, '/path', 'text/plain', ?)",
                docId, wsId, userId
        );

        return docId;
    }

    @Test
    public void testInsertedChunkReceivesNonNullableVector() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        UUID docId = createWorkspaceAndDocument();
        UUID wsId = jdbcTemplate.queryForObject("SELECT workspace_id FROM documents WHERE id = ?", UUID.class, docId);
        UUID chunkId = UUID.randomUUID();
        String vectorJson = create768DimensionVectorJson(1.0);

        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 0, 'Annual leave policy allows 25 days.', 'annual leave policy allows 25 days', ?)",
                chunkId, docId, wsId, vectorJson
        );

        // Verify trigger populated embedding column with non-null vector
        Boolean hasVector = jdbcTemplate.queryForObject(
                "SELECT (embedding IS NOT NULL) FROM document_chunks WHERE id = ?",
                Boolean.class, chunkId
        );
        assertNotNull(hasVector);
        assertTrue(hasVector, "Expected trigger to populate embedding vector(768) on INSERT");

        // Verify dimensions: pgvector vector_dims() function
        Integer dims = jdbcTemplate.queryForObject(
                "SELECT vector_dims(embedding) FROM document_chunks WHERE id = ?",
                Integer.class, chunkId
        );
        assertEquals(768, dims, "Expected vector dimension to be 768");
    }

    @Test
    public void testUpdatingEmbeddingJsonUpdatesVector() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        UUID docId = createWorkspaceAndDocument();
        UUID wsId = jdbcTemplate.queryForObject("SELECT workspace_id FROM documents WHERE id = ?", UUID.class, docId);
        UUID chunkId = UUID.randomUUID();
        String initialVector = create768DimensionVectorJson(1.0);

        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 1, 'Initial content', 'initial content', ?)",
                chunkId, docId, wsId, initialVector
        );

        // Verify initial vector
        String initialVecStr = jdbcTemplate.queryForObject(
                "SELECT embedding::text FROM document_chunks WHERE id = ?",
                String.class, chunkId
        );
        assertNotNull(initialVecStr);
        assertTrue(initialVecStr.startsWith("[1,0,"));

        // Update embedding_json to new vector
        String updatedVector = create768DimensionVectorJson(0.5);
        jdbcTemplate.update(
                "UPDATE document_chunks SET embedding_json = ? WHERE id = ?",
                updatedVector, chunkId
        );

        // Verify vector was updated by trigger
        String updatedVecStr = jdbcTemplate.queryForObject(
                "SELECT embedding::text FROM document_chunks WHERE id = ?",
                String.class, chunkId
        );
        assertNotNull(updatedVecStr);
        assertTrue(updatedVecStr.startsWith("[0.5,0,"));

        // Update non-embedding field (content); vector must remain unchanged
        jdbcTemplate.update(
                "UPDATE document_chunks SET content = 'New Content' WHERE id = ?",
                chunkId
        );
        String unchangedVecStr = jdbcTemplate.queryForObject(
                "SELECT embedding::text FROM document_chunks WHERE id = ?",
                String.class, chunkId
        );
        assertEquals(updatedVecStr, unchangedVecStr, "Expected vector to remain unchanged when embedding_json is not modified");
    }

    @Test
    public void testExistingRecordsBackfill() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        String testSchema = "lifecycle_test";
        String separator = postgres.getJdbcUrl().contains("?") ? "&" : "?";
        String jdbcUrlWithSchema = postgres.getJdbcUrl() + separator + "currentSchema=" + testSchema + ",public";

        DriverManagerDataSource lifecycleDataSource = new DriverManagerDataSource();
        lifecycleDataSource.setDriverClassName("org.postgresql.Driver");
        lifecycleDataSource.setUrl(jdbcUrlWithSchema);
        lifecycleDataSource.setUsername(postgres.getUsername());
        lifecycleDataSource.setPassword(postgres.getPassword());

        JdbcTemplate lifecycleJdbc = new JdbcTemplate(lifecycleDataSource);

        Throwable primaryException = null;
        try {
            // Clean up any stale schema from prior aborted runs and create fresh isolated schema
            lifecycleJdbc.execute("DROP SCHEMA IF EXISTS " + testSchema + " CASCADE");
            lifecycleJdbc.execute("CREATE SCHEMA " + testSchema);

            // Phase 1: Migrate schema up to V2 only (pre-V3 trigger and pre-backfill)
            Flyway flywayV2 = Flyway.configure()
                    .dataSource(lifecycleDataSource)
                    .schemas(testSchema)
                    .defaultSchema(testSchema)
                    .createSchemas(false)
                    .locations("classpath:db/migration")
                    .target("2")
                    .load();
            flywayV2.migrate();

            // Seed user, workspace, and document into the isolated V2 schema
            UUID userId = UUID.randomUUID();
            UUID wsId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            lifecycleJdbc.update(
                    "INSERT INTO " + testSchema + ".users (id, email, password_hash, full_name) VALUES (?, ?, 'hash', 'Legacy User')",
                    userId, "legacy-" + userId + "@test.io"
            );
            lifecycleJdbc.update(
                    "INSERT INTO " + testSchema + ".workspaces (id, name, slug, created_by) VALUES (?, ?, ?, ?)",
                    wsId, "Legacy WS", "legacy-" + wsId, userId
            );
            lifecycleJdbc.update(
                    "INSERT INTO " + testSchema + ".documents (id, workspace_id, title, original_filename, file_type, file_size_bytes, storage_path, mime_type, uploaded_by) " +
                    "VALUES (?, ?, 'Legacy Doc', 'legacy.txt', 'TXT', 100, '/path/legacy.txt', 'text/plain', ?)",
                    docId, wsId, userId
            );

            // Phase 2: Seed 4 distinct legacy chunk rows with embedding = NULL
            UUID validChunkId = UUID.randomUUID();
            UUID malformedChunkId = UUID.randomUUID();
            UUID wrongDimChunkId = UUID.randomUUID();
            UUID nullChunkId = UUID.randomUUID();

            String valid768Json = create768DimensionVectorJson(0.85);

            // 1. Valid 768-dim legacy record
            lifecycleJdbc.update(
                    "INSERT INTO " + testSchema + ".document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json, embedding) " +
                    "VALUES (?, ?, ?, 0, 'Valid legacy content', 'valid legacy content', ?, NULL)",
                    validChunkId, docId, wsId, valid768Json
            );
            // 2. Malformed JSON legacy record
            lifecycleJdbc.update(
                    "INSERT INTO " + testSchema + ".document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json, embedding) " +
                    "VALUES (?, ?, ?, 1, 'Malformed legacy content', 'malformed legacy content', 'not_a_valid_json', NULL)",
                    malformedChunkId, docId, wsId
            );
            // 3. Wrong dimension (3-dim) legacy record
            lifecycleJdbc.update(
                    "INSERT INTO " + testSchema + ".document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json, embedding) " +
                    "VALUES (?, ?, ?, 2, '3D legacy content', '3d legacy content', '[0.1, 0.2, 0.3]', NULL)",
                    wrongDimChunkId, docId, wsId
            );
            // 4. NULL embedding_json legacy record
            lifecycleJdbc.update(
                    "INSERT INTO " + testSchema + ".document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json, embedding) " +
                    "VALUES (?, ?, ?, 3, 'Null legacy content', 'null legacy content', NULL, NULL)",
                    nullChunkId, docId, wsId
            );

            // Phase 3: Execute genuine Flyway migration to V3
            Flyway flywayV3 = Flyway.configure()
                    .dataSource(lifecycleDataSource)
                    .schemas(testSchema)
                    .defaultSchema(testSchema)
                    .createSchemas(false)
                    .locations("classpath:db/migration")
                    .target("3")
                    .load();
            flywayV3.migrate();

            // Phase 4: Assert genuine post-migration database state
            // Valid chunk was backfilled and has exactly 768 dimensions
            Boolean validHasVector = lifecycleJdbc.queryForObject(
                    "SELECT (embedding IS NOT NULL) FROM " + testSchema + ".document_chunks WHERE id = ?",
                    Boolean.class, validChunkId
            );
            assertNotNull(validHasVector);
            assertTrue(validHasVector, "Expected Flyway V3 backfill to populate embedding on valid legacy chunk");

            Integer validDims = lifecycleJdbc.queryForObject(
                    "SELECT vector_dims(embedding) FROM " + testSchema + ".document_chunks WHERE id = ?",
                    Integer.class, validChunkId
            );
            assertEquals(768, validDims, "Expected backfilled vector dimension to be 768");

            // Invalid legacy chunks remain safely NULL without failing the migration
            Boolean malformedHasVector = lifecycleJdbc.queryForObject(
                    "SELECT (embedding IS NOT NULL) FROM " + testSchema + ".document_chunks WHERE id = ?",
                    Boolean.class, malformedChunkId
            );
            assertFalse(malformedHasVector, "Expected malformed JSON to remain embedding IS NULL after V3 migration");

            Boolean wrongDimHasVector = lifecycleJdbc.queryForObject(
                    "SELECT (embedding IS NOT NULL) FROM " + testSchema + ".document_chunks WHERE id = ?",
                    Boolean.class, wrongDimChunkId
            );
            assertFalse(wrongDimHasVector, "Expected 3D embedding to remain embedding IS NULL after V3 migration");

            Boolean nullHasVector = lifecycleJdbc.queryForObject(
                    "SELECT (embedding IS NOT NULL) FROM " + testSchema + ".document_chunks WHERE id = ?",
                    Boolean.class, nullChunkId
            );
            assertFalse(nullHasVector, "Expected NULL embedding_json to remain embedding IS NULL after V3 migration");

        } catch (RuntimeException | Error ex) {
            primaryException = ex;
            throw ex;
        } finally {
            // Clean up isolated schema completely, preserving primary exception if drop fails
            try {
                lifecycleJdbc.execute("DROP SCHEMA IF EXISTS " + testSchema + " CASCADE");
            } catch (RuntimeException | Error cleanupEx) {
                if (primaryException != null) {
                    primaryException.addSuppressed(cleanupEx);
                } else {
                    throw cleanupEx;
                }
            }
        }
    }

    @Test
    public void testMalformedAndWrongDimensionEmbeddingsHandledSafely() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        UUID docId = createWorkspaceAndDocument();
        UUID wsId = jdbcTemplate.queryForObject("SELECT workspace_id FROM documents WHERE id = ?", UUID.class, docId);

        // 1. Malformed JSON
        UUID malformedId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 3, 'Bad JSON', 'bad json', 'not_a_json_array')",
                malformedId, docId, wsId
        );
        Boolean malformedHasVector = jdbcTemplate.queryForObject(
                "SELECT (embedding IS NOT NULL) FROM document_chunks WHERE id = ?",
                Boolean.class, malformedId
        );
        assertFalse(malformedHasVector, "Expected malformed JSON to leave embedding as NULL");

        // 2. Wrong dimension (3 dimensions instead of 768)
        UUID wrongDimId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 4, '3D Vector', '3d vector', '[0.1, 0.2, 0.3]')",
                wrongDimId, docId, wsId
        );
        Boolean wrongDimHasVector = jdbcTemplate.queryForObject(
                "SELECT (embedding IS NOT NULL) FROM document_chunks WHERE id = ?",
                Boolean.class, wrongDimId
        );
        assertFalse(wrongDimHasVector, "Expected wrong-dimension embedding (3 != 768) to leave embedding as NULL");

        // 3. NULL embedding_json
        UUID nullId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 5, 'No vector', 'no vector', NULL)",
                nullId, docId, wsId
        );
        Boolean nullHasVector = jdbcTemplate.queryForObject(
                "SELECT (embedding IS NOT NULL) FROM document_chunks WHERE id = ?",
                Boolean.class, nullId
        );
        assertFalse(nullHasVector, "Expected NULL embedding_json to leave embedding as NULL");
    }

    @Test
    public void testPgVectorSearchServiceReturnsRealResults() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        UUID docId = createWorkspaceAndDocument();
        UUID wsId = jdbcTemplate.queryForObject("SELECT workspace_id FROM documents WHERE id = ?", UUID.class, docId);

        UUID chunk1Id = UUID.randomUUID();
        String c1Vector = create768DimensionVectorJson(1.0);
        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 10, 'Full-time employees receive 25 days annual leave per year.', 'full-time employees receive 25 days annual leave per year', ?)",
                chunk1Id, docId, wsId, c1Vector
        );

        UUID chunk2Id = UUID.randomUUID();
        // Orthogonal vector: set index 1 to 1.0, index 0 to 0.0
        StringBuilder sb2 = new StringBuilder("[0.0,1.0");
        for (int i = 2; i < 768; i++) sb2.append(",0.0");
        sb2.append("]");
        String c2Vector = sb2.toString();

        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 11, 'Kubernetes cluster deployment and container management runbook.', 'kubernetes cluster deployment and container management runbook', ?)",
                chunk2Id, docId, wsId, c2Vector
        );

        DocumentChunk chunk1 = new DocumentChunk(docId, wsId, 10,
                "Full-time employees receive 25 days annual leave per year.",
                "full-time employees receive 25 days annual leave per year", 1, "Leave", 20);
        chunk1.setId(chunk1Id);
        chunk1.setEmbeddingJson(c1Vector);

        DocumentChunk chunk2 = new DocumentChunk(docId, wsId, 11,
                "Kubernetes cluster deployment and container management runbook.",
                "kubernetes cluster deployment and container management runbook", 2, "DevOps", 20);
        chunk2.setId(chunk2Id);
        chunk2.setEmbeddingJson(c2Vector);

        List<Double> queryVector = create768DimensionVectorList(0.95);
        String queryText = "What is the annual leave allowance?";

        // Execute search via PgVectorSearchService against live PostgreSQL + pgvector
        List<ScoredChunk> results = pgVectorSearchService.performHybridSearch(
                List.of(chunk1, chunk2), queryVector, queryText, 5, 0.40
        );

        assertNotNull(results);
        assertFalse(results.isEmpty(), "Expected PgVectorSearchService to return non-empty similarity results");
        assertEquals(chunk1Id, results.get(0).getChunk().getId(), "Expected Chunk 1 (Leave Policy) to rank highest");
        assertTrue(results.get(0).getScore() > 0.6, "Expected high hybrid relevance score");
    }

    @Test
    public void testCandidateFilteringAndWorkspaceIsolation() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        UUID docId1 = createWorkspaceAndDocument();
        UUID wsId1 = jdbcTemplate.queryForObject("SELECT workspace_id FROM documents WHERE id = ?", UUID.class, docId1);

        UUID docId2 = createWorkspaceAndDocument();
        UUID wsId2 = jdbcTemplate.queryForObject("SELECT workspace_id FROM documents WHERE id = ?", UUID.class, docId2);

        UUID chunkAId = UUID.randomUUID();
        String vecA = create768DimensionVectorJson(1.0);
        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 20, 'Workspace A confidential financial strategy report', 'workspace a confidential financial strategy report', ?)",
                chunkAId, docId1, wsId1, vecA
        );

        UUID chunkBId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, content, cleaned_content, embedding_json) " +
                "VALUES (?, ?, ?, 21, 'Workspace B engineering roadmap documentation', 'workspace b engineering roadmap documentation', ?)",
                chunkBId, docId2, wsId2, vecA
        );

        DocumentChunk chunkA = new DocumentChunk(docId1, wsId1, 20,
                "Workspace A confidential financial strategy report",
                "workspace a confidential financial strategy report", 1, "Finance", 20);
        chunkA.setId(chunkAId);
        chunkA.setEmbeddingJson(vecA);

        List<Double> queryVector = create768DimensionVectorList(1.0);

        // When user searches in Workspace A, only Workspace A candidates are passed
        List<ScoredChunk> results = pgVectorSearchService.performHybridSearch(
                List.of(chunkA), queryVector, "financial strategy", 5, 0.30
        );

        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals(chunkAId, results.get(0).getChunk().getId());
    }
}
