package com.knowflow.ai.chat;

import java.time.Instant;
import java.util.UUID;

public class ConversationDto {
    private UUID id;
    private UUID workspaceId;
    private String title;
    private UUID documentFilterId;
    private Instant createdAt;
    private Instant updatedAt;
    private long messageCount;

    public ConversationDto() {
    }

    public ConversationDto(UUID id, UUID workspaceId, String title, UUID documentFilterId, Instant createdAt, Instant updatedAt, long messageCount) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.title = title;
        this.documentFilterId = documentFilterId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getDocumentFilterId() {
        return documentFilterId;
    }

    public void setDocumentFilterId(UUID documentFilterId) {
        this.documentFilterId = documentFilterId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(long messageCount) {
        this.messageCount = messageCount;
    }
}
