package com.knowflow.ai.vector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.document.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Profile("!prod")
public class InMemoryVectorSearchService implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorSearchService.class);
    private static final Pattern WORD_PATTERN = Pattern.compile("\\W+");

    @Value("${knowflow.ai.retrieval.hybrid-weight-vector:0.70}")
    private double vectorWeight;

    @Value("${knowflow.ai.retrieval.hybrid-weight-keyword:0.30}")
    private double keywordWeight;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        double[] queryArray = toPrimitiveArray(queryVector);
        Set<String> queryTokens = extractTokens(queryText);

        List<ScoredChunk> scoredList = new ArrayList<>(candidateChunks.size());

        for (DocumentChunk chunk : candidateChunks) {
            double vScore = 0.0;
            if (chunk.getEmbeddingJson() != null && !chunk.getEmbeddingJson().isBlank()) {
                double[] chunkVector = parseVectorJson(chunk.getEmbeddingJson());
                if (chunkVector != null && chunkVector.length == queryArray.length) {
                    vScore = computeCosineSimilarity(queryArray, chunkVector);
                }
            }

            double kScore = computeKeywordScore(chunk.getCleanedContent(), queryTokens);

            // Normalize vector cosine [-1.0, 1.0] to [0.0, 1.0]
            double normVScore = Math.max(0.0, Math.min(1.0, (vScore + 1.0) / 2.0));
            double hybridScore = (vectorWeight * normVScore) + (keywordWeight * kScore);

            if (hybridScore >= similarityThreshold) {
                scoredList.add(new ScoredChunk(chunk, hybridScore, normVScore, kScore));
            }
        }

        Collections.sort(scoredList);

        return scoredList.stream()
                .limit(topK)
                .collect(Collectors.toList());
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
            log.warn("Failed to parse vector JSON: {}", e.getMessage());
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
