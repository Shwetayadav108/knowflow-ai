package com.knowflow.ai.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LocalFallbackLlmClient implements LlmClient {

    public static final String INSUFFICIENT_INFO_MESSAGE =
            "I couldn't find sufficient information in your authorized documents to answer this question reliably.";

    @Override
    public LlmResult generateResponse(String systemPrompt, List<ChatMessage> history, Map<String, Object> parameters) {
        if (history.isEmpty()) {
            return new LlmResult(INSUFFICIENT_INFO_MESSAGE, 50, 20, 70);
        }

        ChatMessage lastMessage = history.get(history.size() - 1);
        String userQuery = lastMessage.getContent();

        // Check if system prompt contains document context (normal or comparison)
        boolean hasNormalContext = systemPrompt != null && systemPrompt.contains("<document_context>");
        boolean hasComparisonContext = systemPrompt != null && systemPrompt.contains("<document_comparison_context>");
        boolean hasSourceHeader = systemPrompt != null && (systemPrompt.contains("[Source S") || systemPrompt.contains("[Citation"));

        if ((!hasNormalContext && !hasComparisonContext) || !hasSourceHeader) {
            return new LlmResult(INSUFFICIENT_INFO_MESSAGE, 80, 25, 105);
        }

        // Parse context citations out of systemPrompt
        String openTag = hasComparisonContext ? "<document_comparison_context>" : "<document_context>";
        String closeTag = hasComparisonContext ? "</document_comparison_context>" : "</document_context>";
        int ctxStart = systemPrompt.lastIndexOf(openTag);
        int ctxEnd = systemPrompt.lastIndexOf(closeTag);
        if (ctxStart == -1 || ctxEnd == -1 || ctxEnd <= ctxStart) {
            return new LlmResult(INSUFFICIENT_INFO_MESSAGE, 80, 25, 105);
        }

        String context = systemPrompt.substring(ctxStart + openTag.length(), ctxEnd).trim();
        if (context.isBlank()) {
            return new LlmResult(INSUFFICIENT_INFO_MESSAGE, 80, 25, 105);
        }

        // Generate synthesized answer grounded strictly in the context
        StringBuilder answer = new StringBuilder();
        answer.append("Based on your authorized company documents, here is the relevant information regarding your query:\n\n");

        String[] snippets = context.split("\n\n");
        int snippetsUsed = 0;
        for (String snippet : snippets) {
            String trimmed = snippet.trim();
            int sourceHeaderIdx = trimmed.indexOf("[Source S");
            if (sourceHeaderIdx != -1) {
                trimmed = trimmed.substring(sourceHeaderIdx);
            }

            boolean isSourceHeader = trimmed.startsWith("[Source S") && trimmed.contains("]:");
            boolean isLegacyCitationHeader = trimmed.startsWith("[Citation") && trimmed.contains("]:");

            if (isSourceHeader || isLegacyCitationHeader) {
                int colonIdx = trimmed.indexOf("]:");
                String body = trimmed.substring(colonIdx + 2).trim();

                String sourceTag = "";
                if (isSourceHeader) {
                    int colonInHeader = trimmed.indexOf(":");
                    if (colonInHeader != -1 && colonInHeader > 8) {
                        String tagNum = trimmed.substring(8, colonInHeader).trim();
                        sourceTag = "[" + tagNum + "]";
                    }
                }
                if (sourceTag.isEmpty()) {
                    sourceTag = "[S" + (snippetsUsed + 1) + "]";
                }

                answer.append("• ").append(body).append(" ").append(sourceTag).append("\n\n");
                snippetsUsed++;
                if (snippetsUsed >= 3) break;
            }
        }

        if (snippetsUsed == 0) {
            return new LlmResult(INSUFFICIENT_INFO_MESSAGE, 100, 25, 125);
        }

        answer.append("All statements above are directly derived from the verified sources cited above.");

        int promptTokens = (systemPrompt.length() + userQuery.length()) / 4;
        int completionTokens = answer.length() / 4;

        return new LlmResult(answer.toString(), promptTokens, completionTokens, promptTokens + completionTokens);
    }
}
