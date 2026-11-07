package com.knowflow.ai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    @Transactional
    public void logAsync(UUID workspaceId, UUID userId, String action, String resourceType, String resourceId, String details, String ipAddress, String status) {
        try {
            AuditLog auditLog = new AuditLog(workspaceId, userId, action, resourceType, resourceId, details, ipAddress, status);
            auditLogRepository.save(auditLog);
            log.info("Audit logged: action={}, user={}, resource={}:{}", action, userId, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(UUID workspaceId, String action, Pageable pageable) {
        return auditLogRepository.findLogs(workspaceId, action, pageable);
    }
}
