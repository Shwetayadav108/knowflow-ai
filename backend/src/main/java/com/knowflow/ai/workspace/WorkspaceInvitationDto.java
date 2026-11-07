package com.knowflow.ai.workspace;

import java.time.Instant;
import java.util.UUID;

public class WorkspaceInvitationDto {
    private UUID id;
    private UUID workspaceId;
    private String workspaceName;
    private String email;
    private WorkspaceRole role;
    private String token;
    private InvitationStatus status;
    private Instant expiresAt;
    private Instant createdAt;

    public WorkspaceInvitationDto() {
    }

    public WorkspaceInvitationDto(UUID id, UUID workspaceId, String workspaceName, String email, WorkspaceRole role, String token, InvitationStatus status, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.workspaceName = workspaceName;
        this.email = email;
        this.role = role;
        this.token = token;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
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

    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void setRole(WorkspaceRole role) {
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
