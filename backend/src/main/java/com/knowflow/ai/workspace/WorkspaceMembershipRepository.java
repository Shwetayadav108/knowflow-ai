package com.knowflow.ai.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {

    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @Query("SELECT wm FROM WorkspaceMembership wm JOIN FETCH wm.user WHERE wm.workspace.id = :workspaceId")
    List<WorkspaceMembership> findByWorkspaceIdWithUser(@Param("workspaceId") UUID workspaceId);

    void deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);
}
