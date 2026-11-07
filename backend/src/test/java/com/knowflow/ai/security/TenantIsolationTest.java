package com.knowflow.ai.security;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.AccessDeniedException;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.user.UserRepository;
import com.knowflow.ai.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TenantIsolationTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private WorkspaceService workspaceService;

    @Test
    public void testCrossTenantAccessIsBlocked() {
        UUID workspaceId = UUID.randomUUID();
        UUID attackerUserId = UUID.randomUUID();

        // Attacker is NOT a member of workspaceId
        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, attackerUserId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> {
            workspaceService.verifyMembership(workspaceId, attackerUserId);
        });
    }

    @Test
    public void testViewerCannotDeleteWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        UUID viewerUserId = UUID.randomUUID();

        Workspace workspace = new Workspace("Acme Workspace", "acme-slug", "Acme", UUID.randomUUID());
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.of(workspace));

        // User is only a VIEWER
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, viewerUserId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspace, null, WorkspaceRole.VIEWER)));

        assertThrows(AccessDeniedException.class, () -> {
            workspaceService.deleteWorkspace(workspaceId, viewerUserId);
        });
    }
}
