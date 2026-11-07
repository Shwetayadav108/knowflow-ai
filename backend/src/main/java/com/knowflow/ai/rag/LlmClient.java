package com.knowflow.ai.rag;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    LlmResult generateResponse(String systemPrompt, List<ChatMessage> history, Map<String, Object> parameters);

    class LlmResult {
        private final String content;
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;

        public LlmResult(String content, int promptTokens, int completionTokens, int totalTokens) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public String getContent() {
            return content;
        }

        public int getPromptTokens() {
            return promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }
    }
}
