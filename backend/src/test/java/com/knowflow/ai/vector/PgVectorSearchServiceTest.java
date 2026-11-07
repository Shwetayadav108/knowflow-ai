package com.knowflow.ai.vector;

import com.knowflow.ai.document.DocumentChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PgVectorSearchServiceTest {

    @Test
    @DisplayName("Should propagate DataAccessException directly and avoid in-memory fallback")
    public void performHybridSearch_propagatesDataAccessExceptionWithoutFallingBack() {
        // 1. Mock JdbcTemplate and prepare specific DataAccessException instance
        JdbcTemplate mockJdbcTemplate = mock(JdbcTemplate.class);
        CannotGetJdbcConnectionException expectedException =
                new CannotGetJdbcConnectionException("Connection refused: PostgreSQL host unreachable");

        when(mockJdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(expectedException);

        PgVectorSearchService service = new PgVectorSearchService(mockJdbcTemplate);
        ReflectionTestUtils.setField(service, "vectorWeight", 0.70);
        ReflectionTestUtils.setField(service, "keywordWeight", 0.30);

        // 2. Prepare candidate chunk with non-blank embeddingJson
        DocumentChunk chunk = new DocumentChunk(
                UUID.randomUUID(), UUID.randomUUID(), 0,
                "Candidate text content for testing vector propagation",
                "candidate text content for testing vector propagation",
                1, "Section 1", 10
        );
        chunk.setId(UUID.randomUUID());
        chunk.setEmbeddingJson("[0.1, 0.2, 0.3]");

        List<DocumentChunk> candidates = List.of(chunk);
        List<Double> queryVector = List.of(0.1, 0.2, 0.3);
        String queryText = "candidate text";

        // 3. Assert exact same exception instance is propagated
        CannotGetJdbcConnectionException actualException = assertThrows(
                CannotGetJdbcConnectionException.class,
                () -> service.performHybridSearch(candidates, queryVector, queryText, 5, 0.5)
        );

        assertSame(expectedException, actualException,
                "Expected the exact CannotGetJdbcConnectionException instance thrown by JdbcTemplate to be propagated");

        // 4. Verify queryForList was invoked exactly once
        verify(mockJdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }
}
