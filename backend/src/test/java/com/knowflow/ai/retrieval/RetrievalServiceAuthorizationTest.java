package com.knowflow.ai.retrieval;

import com.knowflow.ai.document.*;
import com.knowflow.ai.embedding.EmbeddingClient;
import com.knowflow.ai.vector.VectorSearchService;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RetrievalServiceAuthorizationTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorSearchService vectorSearchService;

    private RetrievalService retrievalService;

    private UUID workspaceId;
    private UUID userId;
    private UUID otherUserId;

    private Document sharedDoc;
    private Document privateDocOwner;
    private Document privateDocOther;
    private Document restrictedDoc;

    @BeforeEach
    public void setup() {
        retrievalService = new RetrievalService(
                documentRepository,
                chunkRepository,
                embeddingClient,
                vectorSearchService
        );
        ReflectionTestUtils.setField(retrievalService, "defaultTopK", 5);
        ReflectionTestUtils.setField(retrievalService, "defaultSimilarityThreshold", 0.60);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        // 1. WORKSPACE_SHARED Document
        sharedDoc = new Document(workspaceId, "Public Handbook", "handbook.pdf", FileType.PDF, 1024L, "/path", "application/pdf", otherUserId);
        sharedDoc.setId(UUID.randomUUID());
        sharedDoc.setStatus(DocumentStatus.READY);
        sharedDoc.setAccessLevel(DocumentAccessLevel.WORKSPACE_SHARED);

        // 2. PRIVATE Document owned by userId
        privateDocOwner = new Document(workspaceId, "User Notes", "notes.txt", FileType.TXT, 512L, "/path", "text/plain", userId);
        privateDocOwner.setId(UUID.randomUUID());
        privateDocOwner.setStatus(DocumentStatus.READY);
        privateDocOwner.setAccessLevel(DocumentAccessLevel.PRIVATE);

        // 3. PRIVATE Document owned by otherUserId
        privateDocOther = new Document(workspaceId, "Secret Salary", "salary.pdf", FileType.PDF, 2048L, "/path", "application/pdf", otherUserId);
        privateDocOther.setId(UUID.randomUUID());
        privateDocOther.setStatus(DocumentStatus.READY);
        privateDocOther.setAccessLevel(DocumentAccessLevel.PRIVATE);

        // 4. RESTRICTED_ROLES Document (requires WORKSPACE_ADMIN or EDITOR)
        restrictedDoc = new Document(workspaceId, "Strategy", "strat.pdf", FileType.PDF, 4096L, "/path", "application/pdf", otherUserId);
        restrictedDoc.setId(UUID.randomUUID());
        restrictedDoc.setStatus(DocumentStatus.READY);
        restrictedDoc.setAccessLevel(DocumentAccessLevel.RESTRICTED_ROLES);
        restrictedDoc.setAllowedRoles("WORKSPACE_ADMIN,EDITOR");
    }

    @Test
    @DisplayName("Fail-closed: when userRole is null, WORKSPACE_SHARED documents are NOT accessible and retrieval returns empty")
    public void testNullUserRole_blocksWorkspaceSharedDocuments() {
        RetrievalFilter filter = new RetrievalFilter(workspaceId, userId, null);
        when(documentRepository.findByWorkspaceIdAndDeletedAtIsNullAndStatusIn(eq(workspaceId), anyCollection()))
                .thenReturn(List.of(sharedDoc, privateDocOwner, privateDocOther, restrictedDoc));

        List<RetrievedPassage> passages = retrievalService.retrieveAuthorizedContext("test query", filter);

        assertTrue(passages.isEmpty(), "Expected empty passages when userRole is null");
        verifyNoInteractions(chunkRepository);
        verifyNoInteractions(embeddingClient);
        verifyNoInteractions(vectorSearchService);
    }

    @Test
    @DisplayName("Authorized VIEWER can access WORKSPACE_SHARED and own PRIVATE docs, but not other PRIVATE or RESTRICTED docs")
    public void testViewerRole_accessRulesPreserved() {
        RetrievalFilter filter = new RetrievalFilter(workspaceId, userId, WorkspaceRole.VIEWER);
        when(documentRepository.findByWorkspaceIdAndDeletedAtIsNullAndStatusIn(eq(workspaceId), anyCollection()))
                .thenReturn(List.of(sharedDoc, privateDocOwner, privateDocOther, restrictedDoc));

        when(chunkRepository.findByWorkspaceIdAndDocumentIdIn(eq(workspaceId), anyList()))
                .thenAnswer(invocation -> {
                    List<UUID> authorizedDocIds = invocation.getArgument(1);
                    // Must include sharedDoc and privateDocOwner, but NOT privateDocOther or restrictedDoc
                    assertTrue(authorizedDocIds.contains(sharedDoc.getId()));
                    assertTrue(authorizedDocIds.contains(privateDocOwner.getId()));
                    assertFalse(authorizedDocIds.contains(privateDocOther.getId()));
                    assertFalse(authorizedDocIds.contains(restrictedDoc.getId()));
                    return Collections.emptyList();
                });

        retrievalService.retrieveAuthorizedContext("test query", filter);

        verify(chunkRepository, times(1)).findByWorkspaceIdAndDocumentIdIn(eq(workspaceId), anyList());
    }

    @Test
    @DisplayName("Authorized WORKSPACE_ADMIN can access RESTRICTED_ROLES documents")
    public void testAdminRole_canAccessRestrictedRolesDocument() {
        RetrievalFilter filter = new RetrievalFilter(workspaceId, userId, WorkspaceRole.WORKSPACE_ADMIN);
        when(documentRepository.findByWorkspaceIdAndDeletedAtIsNullAndStatusIn(eq(workspaceId), anyCollection()))
                .thenReturn(List.of(restrictedDoc));

        when(chunkRepository.findByWorkspaceIdAndDocumentIdIn(eq(workspaceId), anyList()))
                .thenAnswer(invocation -> {
                    List<UUID> authorizedDocIds = invocation.getArgument(1);
                    assertTrue(authorizedDocIds.contains(restrictedDoc.getId()));
                    return Collections.emptyList();
                });

        retrievalService.retrieveAuthorizedContext("test query", filter);

        verify(chunkRepository, times(1)).findByWorkspaceIdAndDocumentIdIn(eq(workspaceId), anyList());
    }

    @Test
    @DisplayName("Verification: passes exact workspaceId and eligible statuses (READY, COMPLETED) to document repository")
    public void testCandidateDocumentRetrieval_passesExactWorkspaceIdAndEligibleStatuses() {
        RetrievalFilter filter = new RetrievalFilter(workspaceId, userId, WorkspaceRole.VIEWER);
        when(documentRepository.findByWorkspaceIdAndDeletedAtIsNullAndStatusIn(eq(workspaceId), anyCollection()))
                .thenReturn(List.of(sharedDoc));

        retrievalService.retrieveAuthorizedContext("test query", filter);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<DocumentStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);

        verify(documentRepository, times(1))
                .findByWorkspaceIdAndDeletedAtIsNullAndStatusIn(eq(workspaceId), statusCaptor.capture());

        Collection<DocumentStatus> capturedStatuses = statusCaptor.getValue();
        assertEquals(2, capturedStatuses.size());
        assertTrue(capturedStatuses.contains(DocumentStatus.READY));
        assertTrue(capturedStatuses.contains(DocumentStatus.COMPLETED));
        assertFalse(capturedStatuses.contains(DocumentStatus.FAILED));
        assertFalse(capturedStatuses.contains(DocumentStatus.PROCESSING));
    }
}
