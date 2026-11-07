package com.knowflow.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Component
@Primary
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    @Value("${knowflow.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${knowflow.ai.gemini.model:gemini-1.5-flash}")
    private String modelName;

    @Value("${knowflow.ai.gemini.allow-fallback:true}")
    private boolean allowFallback;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalFallbackLlmClient fallbackClient;

    @Autowired
    public GeminiLlmClient(LocalFallbackLlmClient fallbackClient) {
        this(fallbackClient, createDefaultRestTemplate());
    }

    public GeminiLlmClient(LocalFallbackLlmClient fallbackClient, RestTemplate restTemplate) {
        this.fallbackClient = fallbackClient;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createDefaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

    @Override
    public LlmResult generateResponse(String systemPrompt, List<ChatMessage> history, Map<String, Object> parameters) {
        if (apiKey == null || apiKey.isBlank()) {
            if (allowFallback) {
                log.warn("Gemini API key is not configured. Falling back to local grounded generator.");
                return fallbackClient.generateResponse(systemPrompt, history, parameters);
            }
            log.error("Gemini API key is not configured and LLM fallback is disabled.");
            throw new LlmException("Gemini API key is not configured and LLM fallback is disabled.");
        }

        String effectiveModel = (modelName != null && !modelName.isBlank()) ? modelName : "gemini-1.5-flash";
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", effectiveModel);

        try {
            List<Map<String, Object>> contents = new ArrayList<>();

            // Add system instruction if available
            Map<String, Object> systemInstruction = Map.of(
                    "parts", List.of(Map.of("text", systemPrompt != null ? systemPrompt : ""))
            );

            // Add conversation messages
            if (history != null) {
                for (ChatMessage msg : history) {
                    if (msg == null) continue;
                    String geminiRole = "assistant".equalsIgnoreCase(msg.getRole()) || "model".equalsIgnoreCase(msg.getRole())
                            ? "model" : "user";
                    contents.add(Map.of(
                            "role", geminiRole,
                            "parts", List.of(Map.of("text", msg.getContent() != null ? msg.getContent() : ""))
                    ));
                }
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("system_instruction", systemInstruction);
            requestBody.put("contents", contents);

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", parameters != null ? parameters.getOrDefault("temperature", 0.2) : 0.2);
            generationConfig.put("maxOutputTokens", parameters != null ? parameters.getOrDefault("maxTokens", 1500) : 1500);
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey.trim());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode candidate = candidates.get(0);
                    JsonNode contentNode = candidate.path("content");
                    JsonNode partsNode = contentNode.path("parts");
                    if (partsNode.isArray() && !partsNode.isEmpty()) {
                        JsonNode textNode = partsNode.get(0).path("text");
                        if (!textNode.isMissingNode() && !textNode.isNull()) {
                            String generatedText = textNode.asText("");
                            if (!generatedText.isBlank()) {
                                int promptTokens = root.path("usageMetadata").path("promptTokenCount").asInt(100);
                                int completionTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(50);
                                int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(promptTokens + completionTokens);

                                return new LlmResult(generatedText, promptTokens, completionTokens, totalTokens);
                            }
                        }
                    }

                    // Candidate present but content, parts or text is missing / blank / safety-blocked
                    String finishReason = candidate.path("finishReason").asText("");
                    String reasonDetail = finishReason.isBlank() ? "empty or missing text in candidate" : "finishReason=" + finishReason;
                    return handleUnusableContent(reasonDetail, systemPrompt, history, parameters);
                }

                // Candidates array is missing or empty
                return handleUnusableContent("no candidates returned", systemPrompt, history, parameters);
            }

            return handleUnusableContent("empty or non-successful response body", systemPrompt, history, parameters);

        } catch (LlmException le) {
            throw le;
        } catch (Exception e) {
            String safeErrorDetail = createSafeErrorMessage(e);
            if (allowFallback) {
                log.warn("Gemini LLM API call failed ({}). Falling back to local grounded generator.", safeErrorDetail);
                return fallbackClient.generateResponse(systemPrompt, history, parameters);
            }
            log.error("Gemini LLM API call failed: {}", safeErrorDetail);
            throw new LlmException("Gemini LLM API call failed: " + safeErrorDetail, e);
        }
    }

    private LlmResult handleUnusableContent(String reason, String systemPrompt, List<ChatMessage> history, Map<String, Object> parameters) {
        if (allowFallback) {
            log.warn("Gemini LLM response contained no usable content ({}). Falling back to local grounded generator.", reason);
            return fallbackClient.generateResponse(systemPrompt, history, parameters);
        }
        log.error("Gemini LLM response contained no usable content ({}) and fallback is disabled.", reason);
        throw new LlmException("Gemini LLM response contained no usable content: " + reason);
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
