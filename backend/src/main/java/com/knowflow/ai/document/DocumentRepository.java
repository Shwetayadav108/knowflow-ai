package com.knowflow.ai.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Document> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    List<Document> findByWorkspaceIdAndDeletedAtIsNullAndStatusIn(UUID workspaceId, Collection<DocumentStatus> statuses);

    @Query("SELECT d FROM Document d WHERE d.workspaceId = :workspaceId AND d.deletedAt IS NULL " +
           "AND (" +
           "    :isWorkspaceAdmin = true " +
           "    OR d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.WORKSPACE_SHARED " +
           "    OR (d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.PRIVATE AND d.uploadedBy = :userId) " +
           "    OR (d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.RESTRICTED_ROLES AND LOWER(d.allowedRoles) LIKE LOWER(CONCAT('%', :roleName, '%')))" +
           ") " +
           "AND (:category IS NULL OR d.category = :category) " +
           "AND (:status IS NULL OR d.status = :status) " +
           "AND (:search IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Document> findAuthorizedDocuments(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("roleName") String roleName,
            @Param("isWorkspaceAdmin") boolean isWorkspaceAdmin,
            @Param("category") String category,
            @Param("status") DocumentStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT DISTINCT d.category FROM Document d WHERE d.workspaceId = :workspaceId AND d.deletedAt IS NULL AND d.category IS NOT NULL " +
           "AND (" +
           "    :isWorkspaceAdmin = true " +
           "    OR d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.WORKSPACE_SHARED " +
           "    OR (d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.PRIVATE AND d.uploadedBy = :userId) " +
           "    OR (d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.RESTRICTED_ROLES AND LOWER(d.allowedRoles) LIKE LOWER(CONCAT('%', :roleName, '%')))" +
           ")")
    List<String> findAuthorizedCategories(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("roleName") String roleName,
            @Param("isWorkspaceAdmin") boolean isWorkspaceAdmin
    );

    @Query("SELECT DISTINCT d.folderPath FROM Document d WHERE d.workspaceId = :workspaceId AND d.deletedAt IS NULL " +
           "AND (" +
           "    :isWorkspaceAdmin = true " +
           "    OR d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.WORKSPACE_SHARED " +
           "    OR (d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.PRIVATE AND d.uploadedBy = :userId) " +
           "    OR (d.accessLevel = com.knowflow.ai.document.DocumentAccessLevel.RESTRICTED_ROLES AND LOWER(d.allowedRoles) LIKE LOWER(CONCAT('%', :roleName, '%')))" +
           ")")
    List<String> findAuthorizedFolders(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("roleName") String roleName,
            @Param("isWorkspaceAdmin") boolean isWorkspaceAdmin
    );

    boolean existsByWorkspaceIdAndOriginalFilenameAndFileSizeBytesAndDeletedAtIsNull(
            UUID workspaceId, String originalFilename, long fileSizeBytes
    );

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    long countByWorkspaceIdAndStatusAndDeletedAtIsNull(UUID workspaceId, DocumentStatus status);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(DocumentStatus status);

    List<Document> findTop5ByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);
}
