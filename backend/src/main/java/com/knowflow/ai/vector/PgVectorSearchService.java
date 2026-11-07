package com.knowflow.ai.vector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.document.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Profile("prod")
public class PgVectorSearchService implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSearchService.class);
    private static final Pattern WORD_PATTERN = Pattern.compile("\\W+");

    private final JdbcTemplate jdbcTemplate;

    @Value("${knowflow.ai.retrieval.hybrid-weight-vector:0.70}")
    private double vectorWeight;

    @Value("${knowflow.ai.retrieval.hybrid-weight-keyword:0.30}")
    private double keywordWeight;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PgVectorSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ScoredChunk> performHybridSearch(
            List<DocumentChunk> candidateChunks,
            List<Double> queryVector,
            String queryText,
            int topK,
            double similarityThreshold
    ) {
        if (candidateChunks.isEmpty() || queryVector == null || queryVector.isEmpty()) {
            return Collections.emptyList();
        }

        // Format query vector into pgvector format: [0.123,0.456,...]
        StringBuilder vectorStr = new StringBuilder("[");
        for (int i = 0; i < queryVector.size(); i++) {
            vectorStr.append(queryVector.get(i));
            if (i < queryVector.size() - 1) {
                vectorStr.append(",");
            }
        }
        vectorStr.append("]");

        Set<String> queryTokens = extractTokens(queryText);
        Map<UUID, DocumentChunk> chunkMap = candidateChunks.stream()
                .collect(Collectors.toMap(DocumentChunk::getId, c -> c, (a, b) -> a));

        List<ScoredChunk> scoredList = new ArrayList<>();

        List<UUID> chunkIds = new ArrayList<>(chunkMap.keySet());
        String inClause = String.join(",", Collections.nCopies(chunkIds.size(), "?"));

        String sql = "SELECT id, 1 - (embedding <=> ?::vector) as cosine_sim " +
                     "FROM document_chunks " +
                     "WHERE id IN (" + inClause + ") AND embedding IS NOT NULL " +
                     "ORDER BY embedding <=> ?::vector LIMIT ?";

        List<Object> params = new ArrayList<>();
        params.add(vectorStr.toString());
        params.addAll(chunkIds);
        params.add(vectorStr.toString());
        params.add(topK * 2);

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql, params.toArray());
        } catch (DataAccessException dae) {
            log.error("PostgreSQL pgvector search query failed for {} candidate chunks: {}",
                    chunkIds.size(), dae.getClass().getSimpleName());
            throw dae;
        }

        if (rows.isEmpty() && !candidateChunks.isEmpty()) {
            long candidatesWithJson = candidateChunks.stream()
                    .filter(c -> c.getEmbeddingJson() != null && !c.getEmbeddingJson().isBlank())
                    .count();
            log.warn("DEGRADED RETRIEVAL: pgvector query returned 0 matching vector rows for {} candidate chunks ({} have non-blank embedding_json). " +
                     "Falling back to in-memory evaluation.",
                     candidateChunks.size(), candidatesWithJson);
            return evaluateInMemory(candidateChunks, queryVector, queryTokens, topK, similarityThreshold);
        }

        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            Number simNum = (Number) row.get("cosine_sim");
            double vScore = simNum != null ? simNum.doubleValue() : 0.0;
            double normVScore = Math.max(0.0, Math.min(1.0, (vScore + 1.0) / 2.0));

            DocumentChunk chunk = chunkMap.get(id);
            if (chunk != null) {
                double kScore = computeKeywordScore(chunk.getCleanedContent(), queryTokens);
                double hybridScore = (vectorWeight * normVScore) + (keywordWeight * kScore);

                if (hybridScore >= similarityThreshold) {
                    scoredList.add(new ScoredChunk(chunk, hybridScore, normVScore, kScore));
                }
            }
        }

        Collections.sort(scoredList);
        return scoredList.stream().limit(topK).collect(Collectors.toList());
    }

    private List<ScoredChunk> evaluateInMemory(
            List<DocumentChunk> candidateChunks,
            List<Double> queryVector,
            Set<String> queryTokens,
            int topK,
            double similarityThreshold
    ) {
        List<ScoredChunk> scoredList = new ArrayList<>();
        double[] queryArray = toPrimitiveArray(queryVector);
        for (DocumentChunk chunk : candidateChunks) {
            double vScore = 0.0;
            if (chunk.getEmbeddingJson() != null && !chunk.getEmbeddingJson().isBlank()) {
                double[] chunkVector = parseVectorJson(chunk.getEmbeddingJson());
                if (chunkVector != null && chunkVector.length == queryArray.length) {
                    vScore = computeCosineSimilarity(queryArray, chunkVector);
                }
            }

            double kScore = computeKeywordScore(chunk.getCleanedContent(), queryTokens);
            double normVScore = Math.max(0.0, Math.min(1.0, (vScore + 1.0) / 2.0));
            double hybridScore = (vectorWeight * normVScore) + (keywordWeight * kScore);

            if (hybridScore >= similarityThreshold) {
                scoredList.add(new ScoredChunk(chunk, hybridScore, normVScore, kScore));
            }
        }

        Collections.sort(scoredList);
        return scoredList.stream().limit(topK).collect(Collectors.toList());
    }

    @Override
    public double computeCosineSimilarity(double[] vectorA, double[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double computeKeywordScore(String text, Set<String> queryTokens) {
        if (text == null || text.isBlank() || queryTokens.isEmpty()) {
            return 0.0;
        }

        String lower = text.toLowerCase();
        int matched = 0;
        for (String token : queryTokens) {
            if (lower.contains(token)) {
                matched++;
            }
        }

        return (double) matched / queryTokens.size();
    }

    private Set<String> extractTokens(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        for (String s : WORD_PATTERN.split(query.toLowerCase())) {
            if (s.length() >= 3) {
                tokens.add(s);
            }
        }
        return tokens;
    }

    private double[] parseVectorJson(String json) {
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
            return toPrimitiveArray(list);
        } catch (Exception e) {
            return null;
        }
    }

    private double[] toPrimitiveArray(List<Double> list) {
        double[] array = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
