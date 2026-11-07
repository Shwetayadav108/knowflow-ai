package com.knowflow.ai.document;

import java.util.UUID;

public class ChunkPreviewDto {
    private UUID id;
    private int chunkIndex;
    private int pageNumber;
    private String sectionHeading;
    private String content;
    private int tokenCount;

    public ChunkPreviewDto() {
    }

    public ChunkPreviewDto(UUID id, int chunkIndex, int pageNumber, String sectionHeading, String content, int tokenCount) {
        this.id = id;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.sectionHeading = sectionHeading;
        this.content = content;
        this.tokenCount = tokenCount;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }
}
