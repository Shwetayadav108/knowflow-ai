package com.knowflow.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Primary
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);

    @Value("${knowflow.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${knowflow.ai.gemini.embedding-model:text-embedding-004}")
    private String embeddingModel;

    @Value("${knowflow.ai.gemini.allow-fallback:true}")
    private boolean allowFallback;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeterministicEmbeddingClient fallbackClient;

    @Autowired
    public GeminiEmbeddingClient(DeterministicEmbeddingClient fallbackClient) {
        this(fallbackClient, new RestTemplate());
    }

    public GeminiEmbeddingClient(DeterministicEmbeddingClient fallbackClient, RestTemplate restTemplate) {
        this.fallbackClient = fallbackClient;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<Double> generateEmbedding(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            if (allowFallback) {
                return fallbackClient.generateEmbedding(text);
            }
            throw new EmbeddingException("Gemini API key is not configured and embedding fallback is disabled.");
        }

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                embeddingModel, apiKey);

        try {
            Map<String, Object> contentMap = Map.of(
                    "parts", List.of(Map.of("text", text != null ? text : ""))
            );
            Map<String, Object> body = Map.of(
                    "model", "models/" + embeddingModel,
                    "content", contentMap
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode valuesNode = root.path("embedding").path("values");
                if (valuesNode.isArray()) {
                    List<Double> values = new ArrayList<>();
                    for (JsonNode val : valuesNode) {
                        values.add(val.asDouble());
                    }
                    return values;
                }
            }
            throw new EmbeddingException("Gemini embedding response missing expected 'embedding.values' structure.");
        } catch (EmbeddingException ee) {
            if (allowFallback) {
                log.warn("Gemini embedding failed: {}. Falling back to deterministic embedding.", ee.getMessage());
                return fallbackClient.generateEmbedding(text);
            }
            throw ee;
        } catch (Exception e) {
            String safeErrorDetail = createSafeErrorMessage(e);
            if (allowFallback) {
                log.warn("Gemini embedding API call failed ({}). Falling back to deterministic embedding.", safeErrorDetail);
                return fallbackClient.generateEmbedding(text);
            }
            log.error("Gemini embedding API call failed: {}", safeErrorDetail);
            throw new EmbeddingException("Gemini embedding API call failed: " + safeErrorDetail, e);
        }
    }

    @Override
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        List<List<Double>> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(generateEmbedding(text));
        }
        return results;
    }

    @Override
    public int getDimension() {
        return DeterministicEmbeddingClient.DIMENSION;
    }

    private String createSafeErrorMessage(Exception e) {
        if (e instanceof HttpStatusCodeException httpEx) {
            return "HTTP " + httpEx.getStatusCode().value() + " " + httpEx.getStatusText();
        } else if (e instanceof ResourceAccessException) {
            return "Connection or timeout error";
        }
        return e.getClass().getSimpleName();
    }
}
