package com.knowflow.ai.rag;

import java.util.List;

public class RagResponse {
    private final String answer;
    private final List<CitationData> citations;
    private final boolean retrievalInsufficient;
    private final int tokensUsed;
    private final long latencyMs;

    public RagResponse(String answer, List<CitationData> citations, boolean retrievalInsufficient, int tokensUsed, long latencyMs) {
        this.answer = answer;
        this.citations = citations;
        this.retrievalInsufficient = retrievalInsufficient;
        this.tokensUsed = tokensUsed;
        this.latencyMs = latencyMs;
    }

    public String getAnswer() {
        return answer;
    }

    public List<CitationData> getCitations() {
        return citations;
    }

    public boolean isRetrievalInsufficient() {
        return retrievalInsufficient;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
