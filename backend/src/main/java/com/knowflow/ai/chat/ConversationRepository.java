package com.knowflow.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT c FROM Conversation c WHERE c.workspaceId = :workspaceId AND c.userId = :userId AND c.deletedAt IS NULL ORDER BY c.updatedAt DESC")
    List<Conversation> findByWorkspaceIdAndUserId(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    @Query("SELECT c FROM Conversation c WHERE c.workspaceId = :workspaceId AND c.userId = :userId AND c.deletedAt IS NULL " +
           "AND LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY c.updatedAt DESC")
    List<Conversation> searchConversations(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId, @Param("query") String query);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    long countByDeletedAtIsNull();
}
