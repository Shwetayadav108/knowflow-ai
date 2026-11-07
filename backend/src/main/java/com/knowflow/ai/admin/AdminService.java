package com.knowflow.ai.admin;

import com.knowflow.ai.analytics.AiUsageRecordRepository;
import com.knowflow.ai.audit.AuditLog;
import com.knowflow.ai.audit.AuditLogRepository;
import com.knowflow.ai.auth.UserDto;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import com.knowflow.ai.document.DocumentChunkRepository;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.document.DocumentStatus;
import com.knowflow.ai.user.GlobalRole;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import com.knowflow.ai.user.UserStatus;
import com.knowflow.ai.workspace.Workspace;
import com.knowflow.ai.workspace.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AiUsageRecordRepository aiUsageRecordRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${knowflow.ai.gemini.model:gemini-1.5-flash}")
    private String modelName;

    @Value("${knowflow.storage.type:local}")
    private String storageType;

    public AdminService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            AiUsageRecordRepository aiUsageRecordRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.aiUsageRecordRepository = aiUsageRecordRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AdminSystemStatsDto getSystemStats() {
        AdminSystemStatsDto stats = new AdminSystemStatsDto();
        stats.setTotalUsers(userRepository.countByDeletedAtIsNull());
        stats.setActiveUsers(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.ACTIVE));
        stats.setSuspendedUsers(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.SUSPENDED));

        stats.setTotalWorkspaces(workspaceRepository.countByDeletedAtIsNull());
        stats.setTotalDocuments(documentRepository.countByDeletedAtIsNull());
        stats.setIndexedDocuments(documentRepository.countByStatusAndDeletedAtIsNull(DocumentStatus.READY)
                + documentRepository.countByStatusAndDeletedAtIsNull(DocumentStatus.COMPLETED));
        stats.setFailedDocuments(documentRepository.countByStatusAndDeletedAtIsNull(DocumentStatus.FAILED));
        stats.setTotalChunks(chunkRepository.count());

        long promptTokens = aiUsageRecordRepository.sumGlobalPromptTokens();
        long completionTokens = aiUsageRecordRepository.sumGlobalCompletionTokens();
        long totalTokens = aiUsageRecordRepository.sumGlobalTokens();
        stats.setTotalGlobalTokens(totalTokens);
        stats.setAiRequestVolume(aiUsageRecordRepository.count());

        // Gemini 1.5 Flash pricing: $0.075 per 1M prompt tokens, $0.30 per 1M completion tokens
        double promptCost = (promptTokens / 1_000_000.0) * 0.075;
        double completionCost = (completionTokens / 1_000_000.0) * 0.30;
        stats.setEstimatedCostUsd(Math.round((promptCost + completionCost) * 10000.0) / 10000.0);

        stats.setSystemHealth("OPERATIONAL");
        stats.setActiveAiModel(modelName);
        stats.setStorageEngine(storageType.toUpperCase());

        Page<AuditLog> recentLogs = auditLogRepository.findLogs(null, null, PageRequest.of(0, 10));
        stats.setRecentActivity(recentLogs.getContent());

        return stats;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAllActiveUsers().stream()
                .map(UserDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto updateUserRole(UUID userId, GlobalRole newRole) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setGlobalRole(newRole);
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto updateUserStatus(UUID userId, UserStatus newStatus) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setStatus(newStatus);
        return UserDto.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<Workspace> getAllWorkspaces() {
        return workspaceRepository.findAll().stream()
                .filter(w -> w.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findLogs(null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getGlobalAuditLogs(Pageable pageable) {
        return auditLogRepository.findLogs(null, null, pageable);
    }
}
