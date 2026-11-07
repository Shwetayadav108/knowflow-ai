package com.knowflow.ai.chat;

import java.util.UUID;

public class CitationDto {
    private UUID id;
    private UUID chunkId;
    private UUID documentId;
    private String documentTitle;
    private int pageNumber;
    private String sectionHeading;
    private String supportingPassage;
    private double relevanceScore;

    public CitationDto() {
    }

    public static CitationDto from(MessageCitation c) {
        CitationDto dto = new CitationDto();
        dto.setId(c.getId());
        dto.setChunkId(c.getChunkId());
        dto.setDocumentId(c.getDocumentId());
        dto.setDocumentTitle(c.getDocumentTitle());
        dto.setPageNumber(c.getPageNumber());
        dto.setSectionHeading(c.getSectionHeading());
        dto.setSupportingPassage(c.getSupportingPassage());
        dto.setRelevanceScore(c.getRelevanceScore());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSectionHeading() {
        return sectionHeading;
    }

    public void setSectionHeading(String sectionHeading) {
        this.sectionHeading = sectionHeading;
    }

    public String getSupportingPassage() {
        return supportingPassage;
    }

    public void setSupportingPassage(String supportingPassage) {
        this.supportingPassage = supportingPassage;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
