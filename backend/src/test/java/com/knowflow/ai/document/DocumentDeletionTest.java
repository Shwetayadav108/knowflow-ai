package com.knowflow.ai.document;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.AccessDeniedException;
import com.knowflow.ai.common.exception.KnowFlowException;
import com.knowflow.ai.ingestion.IngestionWorker;
import com.knowflow.ai.security.DocumentAuthorizationService;
import com.knowflow.ai.storage.LocalStorageService;
import com.knowflow.ai.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentDeletionTest {

    @TempDir
    Path tempStorageDir;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private DocumentAuthorizationService authService;

    @Mock
    private IngestionWorker ingestionWorker;

    @Mock
    private AuditService auditService;

    private LocalStorageService localStorageService;
    private DocumentService documentService;

    private UUID workspaceId;
    private UUID adminUserId;
    private UUID viewerUserId;

    @BeforeEach
    public void setup() {
        workspaceId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        viewerUserId = UUID.randomUUID();

        localStorageService = new LocalStorageService();
        ReflectionTestUtils.setField(localStorageService, "rootStorageDir", tempStorageDir.toString());
        localStorageService.init();

        documentService = new DocumentService(
                documentRepository,
                chunkRepository,
                localStorageService,
                workspaceService,
                authService,
                ingestionWorker,
                auditService
        );
    }

    private Document createTestDocument(String filename, String content) throws IOException {
        String storagePath = localStorageService.store(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                filename,
                workspaceId
        );

        Document doc = new Document(
                workspaceId,
                "Test Title",
                filename,
                FileType.TXT,
                content.length(),
                storagePath,
                "text/plain",
                adminUserId
        );
        doc.setId(UUID.randomUUID());
        return doc;
    }

    @Test
    @DisplayName("Permanent deletion successfully deletes physical file and database records")
    public void testPermanentDeleteSuccessCleansUpPhysicalFileAndDatabase() throws Exception {
        Document doc = createTestDocument("contracts.txt", "confidential contract details");
        Path physicalFile = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalFile)).isTrue();

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc, adminUserId)).thenReturn(true);

        documentService.permanentDeleteDocument(workspaceId, doc.getId(), adminUserId);

        // Verify physical file was deleted
        assertThat(Files.exists(physicalFile)).isFalse();

        // Verify database records were removed
        verify(chunkRepository).deleteByDocumentId(doc.getId());
        verify(documentRepository).delete(doc);
        verify(auditService).logAsync(eq(workspaceId), eq(adminUserId), eq("DOCUMENT_PERMANENTLY_DELETED"),
                eq("DOCUMENT"), eq(doc.getId().toString()), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("Soft deletion preserves physical file on disk and only sets deleted_at")
    public void testSoftDeletePreservesPhysicalFile() throws Exception {
        Document doc = createTestDocument("policy.txt", "company policies");
        Path physicalFile = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalFile)).isTrue();

        when(documentRepository.findByIdAndDeletedAtIsNull(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc.getId(), adminUserId)).thenReturn(true);

        documentService.deleteDocument(doc.getId(), adminUserId);

        // Verify physical file is STILL on disk (not orphaned, preserved for soft-delete)
        assertThat(Files.exists(physicalFile)).isTrue();

        // Verify document was marked deletedAt and saved
        assertThat(doc.getDeletedAt()).isNotNull();
        verify(documentRepository).save(doc);
        verify(chunkRepository).deleteByDocumentId(doc.getId());
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test
    @DisplayName("Permanent deletion handles missing physical file gracefully without failing")
    public void testPermanentDeleteHandlesMissingFileGracefully() throws Exception {
        Document doc = createTestDocument("missing.txt", "file that will be deleted externally");
        Path physicalFile = Path.of(doc.getStoragePath());
        Files.delete(physicalFile);
        assertThat(Files.exists(physicalFile)).isFalse();

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc, adminUserId)).thenReturn(true);

        // Should complete without error
        documentService.permanentDeleteDocument(doc.getId(), adminUserId);

        verify(chunkRepository).deleteByDocumentId(doc.getId());
        verify(documentRepository).delete(doc);
    }

    @Test
    @DisplayName("Database failure during deletion preserves physical file on disk")
    public void testDatabaseFailurePreservesPhysicalFile() throws Exception {
        Document doc = createTestDocument("important.txt", "critical data");
        Path physicalFile = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalFile)).isTrue();

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc, adminUserId)).thenReturn(true);
        doThrow(new RuntimeException("Simulated database failure during chunk deletion"))
                .when(chunkRepository).deleteByDocumentId(doc.getId());

        assertThatThrownBy(() -> documentService.permanentDeleteDocument(workspaceId, doc.getId(), adminUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated database failure");

        // Verify physical file was NOT deleted because DB operation failed
        assertThat(Files.exists(physicalFile)).isTrue();
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test
    @DisplayName("Physical file is only deleted after database transaction commits")
    public void testTransactionalCommitTriggersPhysicalFileDeletion() throws Exception {
        Document doc = createTestDocument("tx-commit.txt", "content for transaction commit test");
        Path physicalFile = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalFile)).isTrue();

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc, adminUserId)).thenReturn(true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.permanentDeleteDocument(workspaceId, doc.getId(), adminUserId);

            // DB records marked for deletion
            verify(chunkRepository).deleteByDocumentId(doc.getId());
            verify(documentRepository).delete(doc);

            // Prior to commit: file must STILL exist on disk!
            assertThat(Files.exists(physicalFile)).isTrue();

            // Simulate transaction commit
            TransactionSynchronizationUtils.triggerAfterCommit();

            // After commit: file is physically deleted
            assertThat(Files.exists(physicalFile)).isFalse();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Transaction rollback ensures physical file is never deleted")
    public void testTransactionalRollbackPreservesPhysicalFile() throws Exception {
        Document doc = createTestDocument("tx-rollback.txt", "content for rollback test");
        Path physicalFile = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalFile)).isTrue();

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc, adminUserId)).thenReturn(true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.permanentDeleteDocument(workspaceId, doc.getId(), adminUserId);

            // Simulate transaction rollback (no afterCommit triggered)
            TransactionSynchronizationManager.clearSynchronization();

            // File must still exist on disk
            assertThat(Files.exists(physicalFile)).isTrue();
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("Unauthorized user cannot delete document and physical file is untouched")
    public void testUnauthorizedUserCannotDeleteDocument() throws Exception {
        Document doc = createTestDocument("secret.txt", "top secret document");
        Path physicalFile = Path.of(doc.getStoragePath());

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc, viewerUserId)).thenReturn(false);

        assertThatThrownBy(() -> documentService.permanentDeleteDocument(doc.getId(), viewerUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");

        // File must remain completely untouched
        assertThat(Files.exists(physicalFile)).isTrue();
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test
    @DisplayName("Workspace isolation prevents deleting document belonging to another workspace")
    public void testWorkspaceIsolationEnforcedDuringDeletion() throws Exception {
        Document doc = createTestDocument("other-ws.txt", "belongs to workspace A");
        Path physicalFile = Path.of(doc.getStoragePath());
        UUID differentWorkspaceId = UUID.randomUUID();

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.permanentDeleteDocument(differentWorkspaceId, doc.getId(), adminUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Document does not belong to the specified workspace");

        assertThat(Files.exists(physicalFile)).isTrue();
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test
    @DisplayName("Path traversal attempts outside storage boundary are rejected")
    public void testPathTraversalOutsideRootBoundaryRejected() {
        String traversalPath = tempStorageDir.resolve("../../etc/passwd").toString();

        assertThatThrownBy(() -> localStorageService.delete(traversalPath, workspaceId))
                .isInstanceOf(KnowFlowException.class)
                .hasMessageContaining("outside document storage boundary");
    }

    @Test
    @DisplayName("Path traversal escaping workspace boundary is rejected")
    public void testPathTraversalEscapingWorkspaceBoundaryRejected() {
        UUID otherWorkspace = UUID.randomUUID();
        Path otherWorkspaceFile = tempStorageDir.resolve(otherWorkspace.toString()).resolve("file.txt");

        assertThatThrownBy(() -> localStorageService.delete(otherWorkspaceFile.toString(), workspaceId))
                .isInstanceOf(KnowFlowException.class)
                .hasMessageContaining("escapes workspace storage boundary");
    }

    @Test
    @DisplayName("Permanent deletion successfully cleans up an already soft-deleted document")
    public void testPermanentDeleteOfAlreadySoftDeletedDocument() throws Exception {
        Document doc = createTestDocument("archived.txt", "archived content");
        doc.setDeletedAt(Instant.now());
        Path physicalFile = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalFile)).isTrue();

        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(authService.canEditOrDeleteDocument(doc, adminUserId)).thenReturn(true);

        documentService.permanentDeleteDocument(workspaceId, doc.getId(), adminUserId);

        assertThat(Files.exists(physicalFile)).isFalse();
        verify(documentRepository).delete(doc);
    }

    @Test
    @DisplayName("Attempting to delete a directory is rejected as security violation")
    public void testDeleteRejectsDirectoryTarget() throws Exception {
        Path workspaceDir = tempStorageDir.resolve(workspaceId.toString());
        Files.createDirectories(workspaceDir);

        assertThatThrownBy(() -> localStorageService.delete(workspaceDir.toString(), workspaceId))
                .isInstanceOf(KnowFlowException.class)
                .hasMessageContaining("does not refer to a regular file");
    }

    @Test
    @DisplayName("Attempting to delete a symbolic link is rejected as security violation")
    public void testDeleteRejectsSymbolicLink() throws Exception {
        Path targetFile = tempStorageDir.resolve("target.txt");
        Files.writeString(targetFile, "target content");

        Path linkFile = tempStorageDir.resolve(workspaceId.toString()).resolve("symlink.txt");
        try {
            Files.createSymbolicLink(linkFile, targetFile);
        } catch (UnsupportedOperationException | FileSystemException | SecurityException e) {
            org.junit.jupiter.api.Assumptions.abort("Filesystem does not support symbolic links without elevation");
        }

        assertThatThrownBy(() -> localStorageService.delete(linkFile.toString(), workspaceId))
                .isInstanceOf(KnowFlowException.class)
                .hasMessageContaining("symbolic links are not permitted");

        // Target file must be untouched
        assertThat(Files.exists(targetFile)).isTrue();
    }

    @Test
    @DisplayName("Attempting to load a symbolic link as resource is rejected")
    public void testLoadAsResourceRejectsSymbolicLink() throws Exception {
        Path targetFile = tempStorageDir.resolve("target.txt");
        Files.writeString(targetFile, "target content");

        Path linkFile = tempStorageDir.resolve(workspaceId.toString()).resolve("symlink-load.txt");
        try {
            Files.createSymbolicLink(linkFile, targetFile);
        } catch (UnsupportedOperationException | FileSystemException | SecurityException e) {
            org.junit.jupiter.api.Assumptions.abort("Filesystem does not support symbolic links without elevation");
        }

        assertThatThrownBy(() -> localStorageService.loadAsResource(linkFile.toString()))
                .isInstanceOf(KnowFlowException.class)
                .hasMessageContaining("symbolic links are not permitted");
    }
}
