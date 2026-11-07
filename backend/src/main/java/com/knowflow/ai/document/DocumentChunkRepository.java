package com.knowflow.ai.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    long countByWorkspaceId(UUID workspaceId);

    @Query("SELECT c FROM DocumentChunk c WHERE c.workspaceId = :workspaceId AND c.documentId IN :documentIds")
    List<DocumentChunk> findByWorkspaceIdAndDocumentIdIn(
            @Param("workspaceId") UUID workspaceId,
            @Param("documentIds") List<UUID> documentIds
    );

    @Query("SELECT c FROM DocumentChunk c WHERE c.workspaceId = :workspaceId")
    List<DocumentChunk> findByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
