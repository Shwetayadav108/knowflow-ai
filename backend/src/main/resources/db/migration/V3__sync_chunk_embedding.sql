-- V3__sync_chunk_embedding.sql - Synchronize and backfill document_chunks.embedding vector(768)
-- PostgreSQL 15+ / 16+ Migration for pgvector synchronization

-- 1. Function to safely parse, validate, and convert embedding_json into vector(768)
CREATE OR REPLACE FUNCTION parse_embedding_json_to_vector(json_text TEXT)
RETURNS vector(768) AS $$
DECLARE
    parsed_json JSONB;
BEGIN
    -- Skip NULL or blank text
    IF json_text IS NULL OR TRIM(json_text) = '' THEN
        RETURN NULL;
    END IF;

    -- Attempt JSON parsing; malformed JSON will raise an exception caught below
    BEGIN
        parsed_json := json_text::jsonb;
    EXCEPTION WHEN OTHERS THEN
        RETURN NULL;
    END;

    -- Ensure root element is a JSON array
    IF jsonb_typeof(parsed_json) != 'array' THEN
        RETURN NULL;
    END IF;

    -- Strictly enforce dimensional contract: exactly 768 float values
    IF jsonb_array_length(parsed_json) != 768 THEN
        RETURN NULL;
    END IF;

    -- Convert valid JSON array to pgvector vector(768)
    RETURN json_text::vector(768);
EXCEPTION WHEN OTHERS THEN
    -- Safety catch for numeric conversion errors or invalid float elements
    RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 2. Trigger function to synchronize embedding on INSERT or UPDATE
CREATE OR REPLACE FUNCTION trg_sync_chunk_embedding()
RETURNS TRIGGER AS $$
BEGIN
    -- On INSERT: populate embedding from embedding_json if provided
    IF TG_OP = 'INSERT' THEN
        IF NEW.embedding_json IS NOT NULL AND TRIM(NEW.embedding_json) != '' THEN
            NEW.embedding := parse_embedding_json_to_vector(NEW.embedding_json);
        END IF;
        RETURN NEW;
    END IF;

    -- On UPDATE: only re-compute if embedding_json actually changed
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.embedding_json IS DISTINCT FROM OLD.embedding_json) THEN
            IF NEW.embedding_json IS NOT NULL AND TRIM(NEW.embedding_json) != '' THEN
                NEW.embedding := parse_embedding_json_to_vector(NEW.embedding_json);
            ELSE
                NEW.embedding := NULL;
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3. Attach BEFORE INSERT OR UPDATE trigger on document_chunks
-- BEFORE trigger directly modifies NEW.embedding in-flight without firing recursive updates
DROP TRIGGER IF EXISTS trg_sync_document_chunks_embedding ON document_chunks;

CREATE TRIGGER trg_sync_document_chunks_embedding
BEFORE INSERT OR UPDATE ON document_chunks
FOR EACH ROW
EXECUTE FUNCTION trg_sync_chunk_embedding();

-- 4. Safe Backfill of existing records where embedding is NULL
-- Safely backfills existing rows with valid 768-dim embeddings; skips malformed/blank records
UPDATE document_chunks
SET embedding = parse_embedding_json_to_vector(embedding_json)
WHERE embedding_json IS NOT NULL
  AND TRIM(embedding_json) != ''
  AND embedding IS NULL;
