package com.knowflow.ai.admin;

import com.knowflow.ai.audit.AuditLog;
import java.util.List;

public class AdminSystemStatsDto {
    private long totalUsers;
    private long activeUsers;
    private long suspendedUsers;
    private long totalWorkspaces;
    private long totalDocuments;
    private long indexedDocuments;
    private long failedDocuments;
    private long totalChunks;
    private long totalGlobalTokens;
    private long aiRequestVolume;
    private double estimatedCostUsd;
    private String systemHealth;
    private String activeAiModel;
    private String storageEngine;
    private List<AuditLog> recentActivity;

    public AdminSystemStatsDto() {
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public long getSuspendedUsers() {
        return suspendedUsers;
    }

    public void setSuspendedUsers(long suspendedUsers) {
        this.suspendedUsers = suspendedUsers;
    }

    public long getTotalWorkspaces() {
        return totalWorkspaces;
    }

    public void setTotalWorkspaces(long totalWorkspaces) {
        this.totalWorkspaces = totalWorkspaces;
    }

    public long getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(long totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public long getIndexedDocuments() {
        return indexedDocuments;
    }

    public void setIndexedDocuments(long indexedDocuments) {
        this.indexedDocuments = indexedDocuments;
    }

    public long getFailedDocuments() {
        return failedDocuments;
    }

    public void setFailedDocuments(long failedDocuments) {
        this.failedDocuments = failedDocuments;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(long totalChunks) {
        this.totalChunks = totalChunks;
    }

    public long getTotalGlobalTokens() {
        return totalGlobalTokens;
    }

    public void setTotalGlobalTokens(long totalGlobalTokens) {
        this.totalGlobalTokens = totalGlobalTokens;
    }

    public long getAiRequestVolume() {
        return aiRequestVolume;
    }

    public void setAiRequestVolume(long aiRequestVolume) {
        this.aiRequestVolume = aiRequestVolume;
    }

    public double getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(double estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public String getSystemHealth() {
        return systemHealth;
    }

    public void setSystemHealth(String systemHealth) {
        this.systemHealth = systemHealth;
    }

    public String getActiveAiModel() {
        return activeAiModel;
    }

    public void setActiveAiModel(String activeAiModel) {
        this.activeAiModel = activeAiModel;
    }

    public String getStorageEngine() {
        return storageEngine;
    }

    public void setStorageEngine(String storageEngine) {
        this.storageEngine = storageEngine;
    }

    public List<AuditLog> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<AuditLog> recentActivity) {
        this.recentActivity = recentActivity;
    }
}
