package com.knowflow.ai.retrieval;

import com.knowflow.ai.workspace.WorkspaceRole;
import java.util.UUID;

public class RetrievalFilter {
    private UUID workspaceId;
    private UUID userId;
    private WorkspaceRole userWorkspaceRole;
    private UUID documentFilterId;
    private String category;
    private Integer topK;
    private Double similarityThreshold;

    public RetrievalFilter() {
    }

    public RetrievalFilter(UUID workspaceId, UUID userId, WorkspaceRole userWorkspaceRole) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.userWorkspaceRole = userWorkspaceRole;
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

    public WorkspaceRole getUserWorkspaceRole() {
        return userWorkspaceRole;
    }

    public void setUserWorkspaceRole(WorkspaceRole userWorkspaceRole) {
        this.userWorkspaceRole = userWorkspaceRole;
    }

    public UUID getDocumentFilterId() {
        return documentFilterId;
    }

    public void setDocumentFilterId(UUID documentFilterId) {
        this.documentFilterId = documentFilterId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
