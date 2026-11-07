package com.knowflow.ai.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GeminiLlmClientTest {

    @Mock
    private LocalFallbackLlmClient fallbackClient;

    @Mock
    private RestTemplate restTemplate;

    private GeminiLlmClient client;

    @BeforeEach
    public void setup() {
        client = new GeminiLlmClient(fallbackClient, restTemplate);
        ReflectionTestUtils.setField(client, "modelName", "gemini-1.5-flash");
    }

    @Test
    @DisplayName("1. Blank API key with fallback enabled returns local fallback response")
    public void testBlankApiKeyWithFallbackEnabled_returnsFallback() {
        ReflectionTestUtils.setField(client, "apiKey", "   ");
        ReflectionTestUtils.setField(client, "allowFallback", true);

        LlmClient.LlmResult mockResult = new LlmClient.LlmResult("Grounded fallback answer", 50, 20, 70);
        when(fallbackClient.generateResponse(anyString(), anyList(), anyMap())).thenReturn(mockResult);

        LlmClient.LlmResult result = client.generateResponse("system prompt", List.of(new ChatMessage("user", "Hello")), Map.of());

        assertNotNull(result);
        assertEquals("Grounded fallback answer", result.getContent());
        verify(fallbackClient, times(1)).generateResponse(anyString(), anyList(), anyMap());
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("2. Blank API key with fallback disabled throws LlmException")
    public void testBlankApiKeyWithFallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("system prompt", List.of(new ChatMessage("user", "Hello")), Map.of())
        );

        assertTrue(exception.getMessage().contains("Gemini API key is not configured and LLM fallback is disabled"));
        verifyNoInteractions(fallbackClient);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("3. Successful Gemini response parses content, sets header, and omits key from URL")
    public void testSuccessfulGeminiResponse() {
        ReflectionTestUtils.setField(client, "apiKey", "secret-test-key-987");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        String jsonResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Enterprise policy answer [1]."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 120,
                    "candidatesTokenCount": 45,
                    "totalTokenCount": 165
                  }
                }
                """;

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(urlCaptor.capture(), entityCaptor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        LlmClient.LlmResult result = client.generateResponse(
                "You are an assistant",
                List.of(new ChatMessage("user", "What is policy?")),
                Map.of("temperature", 0.3)
        );

        assertNotNull(result);
        assertEquals("Enterprise policy answer [1].", result.getContent());
        assertEquals(120, result.getPromptTokens());
        assertEquals(45, result.getCompletionTokens());
        assertEquals(165, result.getTotalTokens());

        // Verify URL does NOT expose API key as query param
        String url = urlCaptor.getValue();
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent", url);
        assertFalse(url.contains("key="));
        assertFalse(url.contains("secret-test-key-987"));

        // Verify x-goog-api-key header
        HttpEntity<Map<String, Object>> entity = entityCaptor.getValue();
        assertEquals("secret-test-key-987", entity.getHeaders().getFirst("x-goog-api-key"));
        assertEquals(MediaType.APPLICATION_JSON, entity.getHeaders().getContentType());

        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("4a. HTTP 400 Bad Request with fallback enabled returns fallback")
    public void testHttp400BadRequest_fallbackEnabled_returnsFallback() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", true);

        HttpClientErrorException rootCause = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(rootCause);

        LlmClient.LlmResult mockFallback = new LlmClient.LlmResult("Fallback answer", 10, 10, 20);
        when(fallbackClient.generateResponse(anyString(), anyList(), anyMap())).thenReturn(mockFallback);

        LlmClient.LlmResult result = client.generateResponse("prompt", List.of(), Map.of());

        assertNotNull(result);
        assertEquals("Fallback answer", result.getContent());
        verify(fallbackClient, times(1)).generateResponse(anyString(), anyList(), anyMap());
    }

    @Test
    @DisplayName("4b. HTTP 403 Forbidden with fallback disabled throws safe LlmException")
    public void testHttp403Forbidden_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        HttpClientErrorException rootCause = new HttpClientErrorException(HttpStatus.FORBIDDEN, "Forbidden");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(rootCause);

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("HTTP 403 Forbidden"));
        assertSame(rootCause, exception.getCause());
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("5. HTTP 429 Rate Limiting with fallback disabled throws LlmException")
    public void testHttp429RateLimiting_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        HttpClientErrorException rootCause = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(rootCause);

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("HTTP 429 Too Many Requests"));
        assertSame(rootCause, exception.getCause());
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("6. HTTP 500 Server Error with fallback disabled throws LlmException")
    public void testHttp500ServerError_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        HttpServerErrorException rootCause = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(rootCause);

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("HTTP 500 Internal Server Error"));
        assertSame(rootCause, exception.getCause());
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("7a. Connection/read timeout with fallback disabled throws LlmException")
    public void testConnectionTimeout_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        ResourceAccessException timeoutEx = new ResourceAccessException("Read timed out");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(timeoutEx);

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("Connection or timeout error"));
        assertSame(timeoutEx, exception.getCause());
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("7b. Network failure with fallback enabled returns fallback")
    public void testNetworkFailure_fallbackEnabled_returnsFallback() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", true);

        ResourceAccessException timeoutEx = new ResourceAccessException("Connection refused");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(timeoutEx);

        LlmClient.LlmResult mockFallback = new LlmClient.LlmResult("Fallback on network failure", 10, 10, 20);
        when(fallbackClient.generateResponse(anyString(), anyList(), anyMap())).thenReturn(mockFallback);

        LlmClient.LlmResult result = client.generateResponse("prompt", List.of(), Map.of());

        assertNotNull(result);
        assertEquals("Fallback on network failure", result.getContent());
        verify(fallbackClient, times(1)).generateResponse(anyString(), anyList(), anyMap());
    }

    @Test
    @DisplayName("8. Empty candidates with fallback disabled throws LlmException without NPE")
    public void testEmptyCandidates_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        String json = "{\"candidates\": []}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("no candidates returned"));
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("9. Safety-blocked response without content throws LlmException with finishReason and no NPE")
    public void testSafetyBlockedResponseWithoutContent_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        String json = """
                {
                  "candidates": [
                    {
                      "finishReason": "SAFETY",
                      "index": 0
                    }
                  ]
                }
                """;
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("finishReason=SAFETY"));
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("10. Missing or empty parts throws LlmException with finishReason when fallback disabled")
    public void testMissingOrEmptyParts_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        String json = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": []
                      },
                      "finishReason": "RECITATION"
                    }
                  ]
                }
                """;
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("finishReason=RECITATION"));
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("11. Missing or blank text throws LlmException when fallback disabled")
    public void testMissingOrBlankText_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        String json = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "   "
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("empty or missing text in candidate"));
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("12. Malformed JSON response throws LlmException when fallback disabled")
    public void testMalformedJsonResponse_fallbackDisabled_throwsLlmException() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        String malformedJson = "{unclosed JSON object";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(malformedJson, HttpStatus.OK));

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        assertTrue(exception.getMessage().contains("JsonParseException") || exception.getMessage().contains("JsonProcessingException")
                || exception.getMessage().contains("Gemini LLM API call failed"));
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("13. Verify fallback is never called when disabled across all failure scenarios")
    public void testFallbackNeverCalledWhenDisabledAcrossAllFailures() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        // Failure A: 500 error
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(LlmException.class, () -> client.generateResponse("p", List.of(), Map.of()));

        // Failure B: 429 error
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));
        assertThrows(LlmException.class, () -> client.generateResponse("p", List.of(), Map.of()));

        // Failure C: Timeout
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));
        assertThrows(LlmException.class, () -> client.generateResponse("p", List.of(), Map.of()));

        // Failure D: Safety block
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}", HttpStatus.OK));
        assertThrows(LlmException.class, () -> client.generateResponse("p", List.of(), Map.of()));

        // Across all failures, fallbackClient must NEVER be interacted with
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("14. Error message does not expose API key or full request URL on I/O exception")
    public void testErrorMessageDoesNotExposeApiKeyOrRequestUrl() {
        String sensitiveKey = "AIzaSy_CONFIDENTIAL_KEY_99999";
        ReflectionTestUtils.setField(client, "apiKey", sensitiveKey);
        ReflectionTestUtils.setField(client, "allowFallback", false);

        // Simulate ResourceAccessException with URL and sensitive query parameter in its message
        String leakProneUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + sensitiveKey;
        ResourceAccessException simulatedIoException = new ResourceAccessException(
                "I/O error on POST request for \"" + leakProneUrl + "\": Connection timed out"
        );

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(simulatedIoException);

        LlmException exception = assertThrows(LlmException.class, () ->
                client.generateResponse("prompt", List.of(), Map.of())
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertFalse(message.contains(sensitiveKey), "Exception message must not contain the sensitive API key");
        assertFalse(message.contains("generativelanguage.googleapis.com"), "Exception message must not contain host/endpoint URL");
        assertFalse(message.contains("key="), "Exception message must not contain key parameter");
        assertEquals("Gemini LLM API call failed: Connection or timeout error", message);
        verifyNoInteractions(fallbackClient);
    }
}
