package com.knowflow.ai.analytics;

import com.knowflow.ai.audit.AuditLog;
import com.knowflow.ai.document.DocumentDto;

import java.util.List;

public class DashboardMetricsDto {
    private long totalDocuments;
    private long processedDocuments;
    private long failedDocuments;
    private long totalChunks;
    private long totalWorkspaces;
    private long totalMembers;
    private long adminCount;
    private long editorCount;
    private long viewerCount;
    private long pendingInvitationsCount;
    private long totalQuestions;
    private long totalTokensConsumed;
    private long aiRequestVolume;
    private double successRatePercent;
    private double averageLatencyMs;
    private List<DocumentDto> recentDocuments;
    private List<AuditLog> recentActivity;
    private List<TimeSeriesPoint> documentsOverTime;
    private List<TimeSeriesPoint> questionsOverTime;

    public DashboardMetricsDto() {
    }

    public long getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(long totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public long getProcessedDocuments() {
        return processedDocuments;
    }

    public void setProcessedDocuments(long processedDocuments) {
        this.processedDocuments = processedDocuments;
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

    public long getTotalWorkspaces() {
        return totalWorkspaces;
    }

    public void setTotalWorkspaces(long totalWorkspaces) {
        this.totalWorkspaces = totalWorkspaces;
    }

    public long getTotalMembers() {
        return totalMembers;
    }

    public void setTotalMembers(long totalMembers) {
        this.totalMembers = totalMembers;
    }

    public long getAdminCount() {
        return adminCount;
    }

    public void setAdminCount(long adminCount) {
        this.adminCount = adminCount;
    }

    public long getEditorCount() {
        return editorCount;
    }

    public void setEditorCount(long editorCount) {
        this.editorCount = editorCount;
    }

    public long getViewerCount() {
        return viewerCount;
    }

    public void setViewerCount(long viewerCount) {
        this.viewerCount = viewerCount;
    }

    public long getPendingInvitationsCount() {
        return pendingInvitationsCount;
    }

    public void setPendingInvitationsCount(long pendingInvitationsCount) {
        this.pendingInvitationsCount = pendingInvitationsCount;
    }

    public long getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(long totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public long getTotalTokensConsumed() {
        return totalTokensConsumed;
    }

    public void setTotalTokensConsumed(long totalTokensConsumed) {
        this.totalTokensConsumed = totalTokensConsumed;
    }

    public long getAiRequestVolume() {
        return aiRequestVolume;
    }

    public void setAiRequestVolume(long aiRequestVolume) {
        this.aiRequestVolume = aiRequestVolume;
    }

    public double getSuccessRatePercent() {
        return successRatePercent;
    }

    public void setSuccessRatePercent(double successRatePercent) {
        this.successRatePercent = successRatePercent;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(double averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }

    public List<DocumentDto> getRecentDocuments() {
        return recentDocuments;
    }

    public void setRecentDocuments(List<DocumentDto> recentDocuments) {
        this.recentDocuments = recentDocuments;
    }

    public List<AuditLog> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<AuditLog> recentActivity) {
        this.recentActivity = recentActivity;
    }

    public List<TimeSeriesPoint> getDocumentsOverTime() {
        return documentsOverTime;
    }

    public void setDocumentsOverTime(List<TimeSeriesPoint> documentsOverTime) {
        this.documentsOverTime = documentsOverTime;
    }

    public List<TimeSeriesPoint> getQuestionsOverTime() {
        return questionsOverTime;
    }

    public void setQuestionsOverTime(List<TimeSeriesPoint> questionsOverTime) {
        this.questionsOverTime = questionsOverTime;
    }
}
