package com.knowflow.ai.retrieval;

import java.util.UUID;

public class RetrievedPassage {
    private final UUID chunkId;
    private final UUID documentId;
    private final String documentTitle;
    private final int pageNumber;
    private final String sectionHeading;
    private final String content;
    private final double relevanceScore;

    public RetrievedPassage(UUID chunkId, UUID documentId, String documentTitle, int pageNumber,
                            String sectionHeading, String content, double relevanceScore) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.pageNumber = pageNumber;
        this.sectionHeading = sectionHeading;
        this.content = content;
        this.relevanceScore = relevanceScore;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getSectionHeading() {
        return sectionHeading;
    }

    public String getContent() {
        return content;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }
}
