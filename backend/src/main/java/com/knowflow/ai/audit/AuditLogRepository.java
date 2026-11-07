package com.knowflow.ai.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE (:workspaceId IS NULL OR a.workspaceId = :workspaceId) " +
           "AND (:action IS NULL OR a.action = :action) ORDER BY a.createdAt DESC")
    Page<AuditLog> findLogs(@Param("workspaceId") UUID workspaceId, @Param("action") String action, Pageable pageable);

    long count();
}
