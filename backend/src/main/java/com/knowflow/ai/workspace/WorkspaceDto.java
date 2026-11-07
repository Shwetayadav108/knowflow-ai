package com.knowflow.ai.workspace;

import java.time.Instant;
import java.util.UUID;

public class WorkspaceDto {
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private WorkspaceRole userRole;
    private long memberCount;
    private long documentCount;
    private Instant createdAt;

    public WorkspaceDto() {
    }

    public WorkspaceDto(UUID id, String name, String slug, String description, WorkspaceRole userRole, long memberCount, long documentCount, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.userRole = userRole;
        this.memberCount = memberCount;
        this.documentCount = documentCount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkspaceRole getUserRole() {
        return userRole;
    }

    public void setUserRole(WorkspaceRole userRole) {
        this.userRole = userRole;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }

    public long getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(long documentCount) {
        this.documentCount = documentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
