package com.knowflow.ai.security;

import com.knowflow.ai.document.Document;
import com.knowflow.ai.document.DocumentAccessLevel;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.document.FileType;
import com.knowflow.ai.workspace.Workspace;
import com.knowflow.ai.workspace.WorkspaceMembership;
import com.knowflow.ai.workspace.WorkspaceMembershipRepository;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DocumentAccessControlTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @InjectMocks
    private DocumentAuthorizationService documentAuthorizationService;

    @Test
    public void testPrivateDocumentCannotBeViewedByOtherMembers() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document privateDoc = new Document(workspaceId, "Secret.pdf", "Secret.pdf", FileType.PDF, 1024, "/path", "application/pdf", ownerId);
        privateDoc.setId(docId);
        privateDoc.setAccessLevel(DocumentAccessLevel.PRIVATE);

        when(documentRepository.findByIdAndDeletedAtIsNull(docId)).thenReturn(Optional.of(privateDoc));

        Workspace ws = new Workspace("WS", "slug", "desc", ownerId);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, otherMemberId))
                .thenReturn(Optional.of(new WorkspaceMembership(ws, null, WorkspaceRole.VIEWER)));

        // Other member cannot view owner's private doc
        boolean canView = documentAuthorizationService.canViewDocument(docId, otherMemberId);
        assertFalse(canView);
    }

    @Test
    public void testRestrictedRoleDocumentAllowsAdminAndBlocksViewer() {
        UUID workspaceId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document restrictedDoc = new Document(workspaceId, "ExecStrategy.pdf", "ExecStrategy.pdf", FileType.PDF, 2048, "/path", "application/pdf", adminId);
        restrictedDoc.setId(docId);
        restrictedDoc.setAccessLevel(DocumentAccessLevel.RESTRICTED_ROLES);
        restrictedDoc.setAllowedRoles("WORKSPACE_ADMIN,EDITOR");

        when(documentRepository.findByIdAndDeletedAtIsNull(docId)).thenReturn(Optional.of(restrictedDoc));

        Workspace ws = new Workspace("WS", "slug", "desc", adminId);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Optional.of(new WorkspaceMembership(ws, null, WorkspaceRole.WORKSPACE_ADMIN)));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, viewerId))
                .thenReturn(Optional.of(new WorkspaceMembership(ws, null, WorkspaceRole.VIEWER)));

        // Admin can view
        assertTrue(documentAuthorizationService.canViewDocument(docId, adminId));

        // Viewer cannot view
        assertFalse(documentAuthorizationService.canViewDocument(docId, viewerId));
    }
}
