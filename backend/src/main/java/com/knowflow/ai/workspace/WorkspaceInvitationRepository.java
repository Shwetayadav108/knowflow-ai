package com.knowflow.ai.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    Optional<WorkspaceInvitation> findByToken(String token);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, InvitationStatus status);

    List<WorkspaceInvitation> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<WorkspaceInvitation> findByEmailAndStatus(String email, InvitationStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, InvitationStatus status);
}
