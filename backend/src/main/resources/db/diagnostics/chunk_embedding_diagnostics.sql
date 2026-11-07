-- ============================================================================
-- KnowFlow AI: PostgreSQL + pgvector Chunk Embedding Diagnostic Script
-- File: backend/src/main/resources/db/diagnostics/chunk_embedding_diagnostics.sql
-- ============================================================================
--
-- PURPOSE:
-- Operational diagnostic script to audit document_chunks embedding persistence,
-- identify synchronization anomalies, and report invalid embedding JSON payloads.
--
-- EXECUTION INSTRUCTIONS:
-- 1. This script is an on-demand operational diagnostic tool, NOT a Flyway migration.
-- 2. Run manually via psql or your PostgreSQL administrative client:
--      psql -U <username> -d <database_name> -f chunk_embedding_diagnostics.sql
-- 3. PREREQUISITE:
--    Migration V3__sync_chunk_embedding.sql must already be applied so that the
--    parse_embedding_json_to_vector(TEXT) function is available.
--
-- DIAGNOSTIC CATEGORIES:
-- 1. VECTOR_PRESENT:
--    Native vector column is non-NULL (embedding IS NOT NULL).
--    IMPORTANT: Indicates binary vector presence in PostgreSQL; does NOT prove
--    equality with embedding_json without explicit vector distance calculation.
-- 2. MISSING_EMBEDDING:
--    Both native vector and JSON text are absent (embedding IS NULL and
--    embedding_json IS NULL or blank). Normal state for chunks currently being
--    ingested or documents uploaded without embeddings.
-- 3. UNSYNCED_VALID_JSON:
--    Native vector is missing (embedding IS NULL), but embedding_json contains
--    a valid 768-dimensional float array accepted by parse_embedding_json_to_vector.
--    IMPORTANT: Indicates syntactic and dimensional validity accepted by the
--    conversion function; does NOT prove semantic embedding correctness.
--    These rows can be remediated directly in PostgreSQL via SQL backfill without
--    re-invoking the Gemini embedding API.
-- 4. UNCONVERTIBLE_VECTOR_JSON:
--    Native vector is missing (embedding IS NULL), nonblank embedding_json is present,
--    but parse_embedding_json_to_vector returned NULL due to malformed JSON syntax,
--    non-array structure, dimension mismatch (!= 768), or non-numeric float values.
--
-- PRIVACY & SECURITY GUARDRAILS:
-- - This script NEVER selects document content, cleaned content, section headings,
--   document titles, filenames, user emails, or raw vector coordinates.
--
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. AGGREGATE HEALTH METRICS
-- ----------------------------------------------------------------------------
-- Uses a MATERIALIZED CTE optimization fence to ensure parse_embedding_json_to_vector()
-- is evaluated strictly on anomaly rows and evaluated exactly once per anomaly row.

WITH anomaly_candidates AS MATERIALIZED (
    SELECT
        id,
        parse_embedding_json_to_vector(embedding_json) AS parsed_vector
    FROM document_chunks
    WHERE embedding IS NULL
      AND embedding_json IS NOT NULL
      AND TRIM(embedding_json) != ''
)
SELECT
    COUNT(*) AS total_chunks,

    -- 1. Native vector is present (embedding IS NOT NULL)
    -- NOTE: Proves native vector presence; does not prove equality with embedding_json.
    COUNT(*) FILTER (
        WHERE dc.embedding IS NOT NULL
    ) AS vector_present,

    -- 2. Blank or NULL embedding text (pending ingestion or skipped)
    COUNT(*) FILTER (
        WHERE dc.embedding IS NULL
          AND (dc.embedding_json IS NULL OR TRIM(dc.embedding_json) = '')
    ) AS missing_embedding,

    -- 3. Anomaly Candidates: nonblank JSON text exists while native vector is NULL
    (SELECT COUNT(*) FROM anomaly_candidates) AS nonblank_json_without_vector,

    -- 4. Sub-category A: Valid 768-dim JSON accepted by parser (remediable via SQL backfill)
    -- NOTE: Proves syntax and dimension validity; does not prove semantic embedding correctness.
    (SELECT COUNT(*) FILTER (WHERE parsed_vector IS NOT NULL) FROM anomaly_candidates) AS unsynced_valid_json,

    -- 5. Sub-category B: Unconvertible JSON rejected by parser
    (SELECT COUNT(*) FILTER (WHERE parsed_vector IS NULL) FROM anomaly_candidates) AS unconvertible_vector_json

FROM document_chunks dc;


-- ----------------------------------------------------------------------------
-- 2. ROW-LEVEL REMEDIATION CANDIDATES (OPERATOR ONLY)
-- ----------------------------------------------------------------------------
-- Refactored into two MATERIALIZED CTEs:
-- - selected_candidates: Isolates rows with unpopulated vectors and nonblank JSON,
--   imposing a deterministic ORDER BY document_id, chunk_index and LIMIT 100.
--   This bounds the working set so that expensive parsing is never executed on more
--   than 100 rows.
-- - classified_candidates: Calls parse_embedding_json_to_vector() once per selected row
--   to assign the diagnostic category (UNSYNCED_VALID_JSON vs UNCONVERTIBLE_VECTOR_JSON).
-- PRIVACY GUARDRAIL: Final SELECT returns strictly operational UUIDs, chunk index,
-- and category. It never exposes document content, embedding values, or filenames.

WITH selected_candidates AS MATERIALIZED (
    SELECT
        id AS chunk_id,
        document_id,
        workspace_id,
        chunk_index,
        embedding_json
    FROM document_chunks
    WHERE embedding IS NULL
      AND embedding_json IS NOT NULL
      AND TRIM(embedding_json) != ''
    ORDER BY document_id, chunk_index
    LIMIT 100
),
classified_candidates AS MATERIALIZED (
    SELECT
        chunk_id,
        document_id,
        workspace_id,
        chunk_index,
        CASE
            WHEN parse_embedding_json_to_vector(embedding_json) IS NOT NULL THEN 'UNSYNCED_VALID_JSON'
            ELSE 'UNCONVERTIBLE_VECTOR_JSON'
        END AS diagnostic_category
    FROM selected_candidates
)
SELECT
    chunk_id,
    document_id,
    workspace_id,
    chunk_index,
    diagnostic_category
FROM classified_candidates
ORDER BY document_id, chunk_index;
