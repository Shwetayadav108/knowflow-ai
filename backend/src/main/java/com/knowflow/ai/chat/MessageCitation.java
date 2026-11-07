package com.knowflow.ai.chat;

import com.knowflow.ai.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "message_citations")
public class MessageCitation extends BaseEntity {

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_title", nullable = false)
    private String documentTitle;

    @Column(name = "page_number")
    private int pageNumber = 1;

    @Column(name = "section_heading")
    private String sectionHeading;

    @Column(name = "supporting_passage", columnDefinition = "text", nullable = false)
    private String supportingPassage;

    @Column(name = "relevance_score", nullable = false)
    private double relevanceScore = 0.0;

    public MessageCitation() {
    }

    public MessageCitation(UUID messageId, UUID chunkId, UUID documentId, String documentTitle,
                           int pageNumber, String sectionHeading, String supportingPassage, double relevanceScore) {
        this.messageId = messageId;
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.pageNumber = pageNumber;
        this.sectionHeading = sectionHeading;
        this.supportingPassage = supportingPassage;
        this.relevanceScore = relevanceScore;
    }

    // Getters and Setters
    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
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
