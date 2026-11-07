package com.knowflow.ai.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, UUID> {

    @Query("SELECT COALESCE(SUM(r.totalTokens), 0) FROM AiUsageRecord r WHERE r.workspaceId = :workspaceId")
    long sumTotalTokensByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT COALESCE(SUM(r.totalTokens), 0) FROM AiUsageRecord r")
    long sumGlobalTokens();

    @Query("SELECT COALESCE(SUM(r.promptTokens), 0) FROM AiUsageRecord r")
    long sumGlobalPromptTokens();

    @Query("SELECT COALESCE(SUM(r.completionTokens), 0) FROM AiUsageRecord r")
    long sumGlobalCompletionTokens();

    long countByWorkspaceId(UUID workspaceId);
}
