package com.knowflow.ai.workspace;

import java.time.Instant;
import java.util.UUID;

public class WorkspaceMemberDto {
    private UUID userId;
    private String email;
    private String fullName;
    private WorkspaceRole role;
    private Instant joinedAt;

    public WorkspaceMemberDto() {
    }

    public WorkspaceMemberDto(UUID userId, String email, String fullName, WorkspaceRole role, Instant joinedAt) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void setRole(WorkspaceRole role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
