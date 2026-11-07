package com.knowflow.ai.workspace;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.AccessDeniedException;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import com.knowflow.ai.common.exception.ValidationException;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository,
            DocumentRepository documentRepository,
            AuditService auditService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> getUserWorkspaces(UUID userId) {
        List<Workspace> workspaces = workspaceRepository.findWorkspacesByUserId(userId);
        List<WorkspaceDto> dtos = new ArrayList<>();

        for (Workspace w : workspaces) {
            WorkspaceRole role = membershipRepository.findByWorkspaceIdAndUserId(w.getId(), userId)
                    .map(WorkspaceMembership::getRole)
                    .orElse(WorkspaceRole.VIEWER);

            long memberCount = membershipRepository.countByWorkspaceId(w.getId());
            long docCount = documentRepository.countByWorkspaceIdAndDeletedAtIsNull(w.getId());

            dtos.add(new WorkspaceDto(
                    w.getId(),
                    w.getName(),
                    w.getSlug(),
                    w.getDescription(),
                    role,
                    memberCount,
                    docCount,
                    w.getCreatedAt()
            ));
        }

        return dtos;
    }

    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspace(UUID workspaceId, UUID userId) {
        Workspace w = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this workspace."));

        long memberCount = membershipRepository.countByWorkspaceId(w.getId());
        long docCount = documentRepository.countByWorkspaceIdAndDeletedAtIsNull(w.getId());

        return new WorkspaceDto(
                w.getId(),
                w.getName(),
                w.getSlug(),
                w.getDescription(),
                membership.getRole(),
                memberCount,
                docCount,
                w.getCreatedAt()
        );
    }

    @Transactional
    public WorkspaceDto createWorkspace(CreateWorkspaceRequest request, UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String baseSlug = request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (baseSlug.isBlank()) {
            baseSlug = "workspace";
        }
        String slug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);

        Workspace workspace = new Workspace(request.getName().trim(), slug, request.getDescription(), userId);
        workspace = workspaceRepository.save(workspace);

        WorkspaceMembership membership = new WorkspaceMembership(workspace, user, WorkspaceRole.WORKSPACE_ADMIN);
        membershipRepository.save(membership);

        auditService.logAsync(workspace.getId(), userId, "WORKSPACE_CREATED", "WORKSPACE", workspace.getId().toString(), "Created workspace: " + workspace.getName(), null, "SUCCESS");

        return new WorkspaceDto(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getDescription(),
                WorkspaceRole.WORKSPACE_ADMIN,
                1L,
                0L,
                workspace.getCreatedAt()
        );
    }

    @Transactional
    public WorkspaceDto updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID userId) {
        Workspace w = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        requireRole(workspaceId, userId, WorkspaceRole.WORKSPACE_ADMIN);

        w.setName(request.getName().trim());
        w.setDescription(request.getDescription());
        workspaceRepository.save(w);

        auditService.logAsync(w.getId(), userId, "WORKSPACE_UPDATED", "WORKSPACE", w.getId().toString(), "Updated workspace name/details", null, "SUCCESS");

        return getWorkspace(workspaceId, userId);
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceId, UUID userId) {
        Workspace w = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        requireRole(workspaceId, userId, WorkspaceRole.WORKSPACE_ADMIN);

        w.setDeletedAt(Instant.now());
        workspaceRepository.save(w);

        auditService.logAsync(w.getId(), userId, "WORKSPACE_DELETED", "WORKSPACE", w.getId().toString(), "Soft deleted workspace", null, "SUCCESS");
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberDto> getMembers(UUID workspaceId, UUID userId) {
        verifyMembership(workspaceId, userId);

        List<WorkspaceMembership> memberships = membershipRepository.findByWorkspaceIdWithUser(workspaceId);
        List<WorkspaceMemberDto> dtos = new ArrayList<>();

        for (WorkspaceMembership m : memberships) {
            dtos.add(new WorkspaceMemberDto(
                    m.getUser().getId(),
                    m.getUser().getEmail(),
                    m.getUser().getFullName(),
                    m.getRole(),
                    m.getCreatedAt()
            ));
        }

        return dtos;
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID memberUserId, UUID userId) {
        requireRole(workspaceId, userId, WorkspaceRole.WORKSPACE_ADMIN);

        if (memberUserId.equals(userId)) {
            throw new ValidationException("You cannot remove yourself as workspace admin. Transfer admin rights or delete the workspace.");
        }

        membershipRepository.deleteByWorkspaceIdAndUserId(workspaceId, memberUserId);
        auditService.logAsync(workspaceId, userId, "MEMBER_REMOVED", "USER", memberUserId.toString(), "Removed user from workspace", null, "SUCCESS");
    }

    public WorkspaceRole getUserRole(UUID workspaceId, UUID userId) {
        return membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMembership::getRole)
                .orElse(null);
    }

    public void verifyMembership(UUID workspaceId, UUID userId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new AccessDeniedException("Access denied: You are not a member of this workspace.");
        }
    }

    public void requireRole(UUID workspaceId, UUID userId, WorkspaceRole minRole) {
        WorkspaceRole userRole = getUserRole(workspaceId, userId);
        if (userRole == null) {
            throw new AccessDeniedException("Access denied: Not a member of this workspace.");
        }

        if (minRole == WorkspaceRole.WORKSPACE_ADMIN && userRole != WorkspaceRole.WORKSPACE_ADMIN) {
            throw new AccessDeniedException("Access denied: Requires WORKSPACE_ADMIN permission.");
        }

        if (minRole == WorkspaceRole.EDITOR && userRole == WorkspaceRole.VIEWER) {
            throw new AccessDeniedException("Access denied: Requires EDITOR permission.");
        }
    }
}
