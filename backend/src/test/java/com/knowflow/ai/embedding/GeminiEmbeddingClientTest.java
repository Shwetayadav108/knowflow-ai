package com.knowflow.ai.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GeminiEmbeddingClientTest {

    @Mock
    private DeterministicEmbeddingClient fallbackClient;

    @Mock
    private RestTemplate restTemplate;

    private GeminiEmbeddingClient client;

    @BeforeEach
    public void setup() {
        client = new GeminiEmbeddingClient(fallbackClient, restTemplate);
        ReflectionTestUtils.setField(client, "embeddingModel", "text-embedding-004");
    }

    @Test
    @DisplayName("Blank key + fallback enabled returns deterministic embedding")
    public void testBlankKeyWithFallbackEnabled_returnsDeterministicEmbedding() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        ReflectionTestUtils.setField(client, "allowFallback", true);

        List<Double> mockVector = List.of(0.1, 0.2, 0.3);
        when(fallbackClient.generateEmbedding("sample text")).thenReturn(mockVector);

        List<Double> result = client.generateEmbedding("sample text");

        assertNotNull(result);
        assertEquals(mockVector, result);
        verify(fallbackClient, times(1)).generateEmbedding("sample text");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Blank key + fallback disabled throws EmbeddingException without invoking fallback")
    public void testBlankKeyWithFallbackDisabled_throwsEmbeddingException() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        EmbeddingException exception = assertThrows(EmbeddingException.class, () ->
                client.generateEmbedding("sample text")
        );

        assertTrue(exception.getMessage().contains("Gemini API key is not configured and embedding fallback is disabled"));
        verifyNoInteractions(fallbackClient);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Gemini API failure + fallback disabled throws EmbeddingException, preserves cause, and does not invoke fallback")
    public void testGeminiApiFailureWithFallbackDisabled_throwsEmbeddingExceptionAndPreservesCause() {
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "allowFallback", false);

        HttpClientErrorException rootCause = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(rootCause);

        EmbeddingException exception = assertThrows(EmbeddingException.class, () ->
                client.generateEmbedding("sample text")
        );

        assertTrue(exception.getMessage().contains("HTTP 429 Too Many Requests"));
        assertNotNull(exception.getCause());
        assertSame(rootCause, exception.getCause());
        verifyNoInteractions(fallbackClient);
    }

    @Test
    @DisplayName("Gemini API failure + fallback enabled returns deterministic fallback")
    public void testGeminiApiFailureWithFallbackEnabled_returnsDeterministicFallback() {
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "allowFallback", true);

        HttpClientErrorException rootCause = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(rootCause);

        List<Double> mockVector = List.of(0.4, 0.5, 0.6);
        when(fallbackClient.generateEmbedding("sample text")).thenReturn(mockVector);

        List<Double> result = client.generateEmbedding("sample text");

        assertNotNull(result);
        assertEquals(mockVector, result);
        verify(fallbackClient, times(1)).generateEmbedding("sample text");
    }
}
