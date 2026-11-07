package com.knowflow.ai.chat;

import com.knowflow.ai.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "document_filter_id")
    private UUID documentFilterId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Conversation() {
    }

    public Conversation(UUID workspaceId, UUID userId, String title) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.title = title;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
