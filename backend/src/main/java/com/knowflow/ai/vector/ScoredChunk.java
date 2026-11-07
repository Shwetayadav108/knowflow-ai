package com.knowflow.ai.vector;

import com.knowflow.ai.document.DocumentChunk;

public class ScoredChunk implements Comparable<ScoredChunk> {
    private final DocumentChunk chunk;
    private final double score;
    private final double vectorScore;
    private final double keywordScore;

    public ScoredChunk(DocumentChunk chunk, double score, double vectorScore, double keywordScore) {
        this.chunk = chunk;
        this.score = score;
        this.vectorScore = vectorScore;
        this.keywordScore = keywordScore;
    }

    public DocumentChunk getChunk() {
        return chunk;
    }

    public double getScore() {
        return score;
    }

    public double getVectorScore() {
        return vectorScore;
    }

    public double getKeywordScore() {
        return keywordScore;
    }

    @Override
    public int compareTo(ScoredChunk o) {
        return Double.compare(o.score, this.score); // descending order
    }
}
