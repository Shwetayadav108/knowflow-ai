package com.knowflow.ai.rag;

import java.util.UUID;

public class CitationData {
    private final UUID chunkId;
    private final UUID documentId;
    private final String documentTitle;
    private final int pageNumber;
    private final String sectionHeading;
    private final String supportingPassage;
    private final double relevanceScore;

    public CitationData(UUID chunkId, UUID documentId, String documentTitle, int pageNumber,
                        String sectionHeading, String supportingPassage, double relevanceScore) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.pageNumber = pageNumber;
        this.sectionHeading = sectionHeading;
        this.supportingPassage = supportingPassage;
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

    public String getSupportingPassage() {
        return supportingPassage;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }
}
