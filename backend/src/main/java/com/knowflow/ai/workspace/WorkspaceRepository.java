package com.knowflow.ai.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findBySlugAndDeletedAtIsNull(String slug);

    Optional<Workspace> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Query("SELECT w FROM Workspace w JOIN WorkspaceMembership wm ON w.id = wm.workspace.id " +
           "WHERE wm.user.id = :userId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    List<Workspace> findWorkspacesByUserId(@Param("userId") UUID userId);

    long countByDeletedAtIsNull();
}
