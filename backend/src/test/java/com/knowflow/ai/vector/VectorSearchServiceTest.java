package com.knowflow.ai.vector;

import com.knowflow.ai.document.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class VectorSearchServiceTest {

    private VectorSearchService vectorSearchService;

    @BeforeEach
    public void setup() {
        vectorSearchService = new InMemoryVectorSearchService();
        ReflectionTestUtils.setField(vectorSearchService, "vectorWeight", 0.70);
        ReflectionTestUtils.setField(vectorSearchService, "keywordWeight", 0.30);
    }

    @Test
    public void testCosineSimilarityCalculation() {
        double[] v1 = new double[]{1.0, 0.0, 0.0};
        double[] v2 = new double[]{1.0, 0.0, 0.0};
        double[] v3 = new double[]{0.0, 1.0, 0.0};

        assertEquals(1.0, vectorSearchService.computeCosineSimilarity(v1, v2), 0.0001);
        assertEquals(0.0, vectorSearchService.computeCosineSimilarity(v1, v3), 0.0001);
    }

    @Test
    public void testHybridSearchRanksRelevantChunkHighest() {
        UUID docId = UUID.randomUUID();
        UUID wsId = UUID.randomUUID();

        DocumentChunk c1 = new DocumentChunk(docId, wsId, 0,
                "Company employees receive 25 days of annual leave per calendar year.",
                "Company employees receive 25 days of annual leave per calendar year.",
                3, "Leave Policy", 20);
        c1.setId(UUID.randomUUID());
        c1.setEmbeddingJson("[1.0, 0.0, 0.0]");

        DocumentChunk c2 = new DocumentChunk(docId, wsId, 1,
                "The engineering build pipeline deploys containers into Kubernetes clusters.",
                "The engineering build pipeline deploys containers into Kubernetes clusters.",
                7, "DevOps Manual", 20);
        c2.setId(UUID.randomUUID());
        c2.setEmbeddingJson("[0.0, 1.0, 0.0]");

        List<Double> queryVector = List.of(0.9, 0.1, 0.0);
        String queryText = "What is the annual leave policy?";

        List<ScoredChunk> results = vectorSearchService.performHybridSearch(
                List.of(c1, c2), queryVector, queryText, 5, 0.40);

        assertFalse(results.isEmpty());
        assertEquals(c1.getId(), results.get(0).getChunk().getId());
        assertTrue(results.get(0).getScore() > 0.6);
    }
}
