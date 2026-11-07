package com.knowflow.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 200;
    private final Map<String, WindowCounter> requestCounts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        long currentMinute = System.currentTimeMillis() / 60000;
        String key = clientIp + ":" + currentMinute;

        WindowCounter counter = requestCounts.computeIfAbsent(key, k -> new WindowCounter());

        if (counter.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Object> apiResponse = ApiResponse.error("Too many requests", "Rate limit exceeded. Please try again in a minute.");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        // Clean old keys occasionally
        if (requestCounts.size() > 5000) {
            requestCounts.entrySet().removeIf(e -> !e.getKey().endsWith(":" + currentMinute));
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        public int incrementAndGet() {
            return count.incrementAndGet();
        }
    }
}
