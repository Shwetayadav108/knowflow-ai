package com.knowflow.ai.document;

import com.knowflow.ai.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk extends BaseEntity {

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "cleaned_content", columnDefinition = "text", nullable = false)
    private String cleanedContent;

    @Column(name = "page_number")
    private int pageNumber = 1;

    @Column(name = "section_heading")
    private String sectionHeading;

    @Column(name = "token_count")
    private int tokenCount = 0;

    @Column(name = "embedding_json", columnDefinition = "text")
    private String embeddingJson;

    public DocumentChunk() {
    }

    public DocumentChunk(UUID documentId, UUID workspaceId, int chunkIndex, String content,
                         String cleanedContent, int pageNumber, String sectionHeading, int tokenCount) {
        this.documentId = documentId;
        this.workspaceId = workspaceId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.cleanedContent = cleanedContent;
        this.pageNumber = pageNumber;
        this.sectionHeading = sectionHeading;
        this.tokenCount = tokenCount;
    }

    // Getters and Setters
    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCleanedContent() {
        return cleanedContent;
    }

    public void setCleanedContent(String cleanedContent) {
        this.cleanedContent = cleanedContent;
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

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getEmbeddingJson() {
        return embeddingJson;
    }

    public void setEmbeddingJson(String embeddingJson) {
        this.embeddingJson = embeddingJson;
    }
}
