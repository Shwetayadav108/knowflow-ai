package com.knowflow.ai.workspace;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.AccessDeniedException;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import com.knowflow.ai.common.exception.ValidationException;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvitationService {

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationService(
            WorkspaceInvitationRepository invitationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.invitationRepository = invitationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Transactional
    public WorkspaceInvitationDto createInvitation(UUID workspaceId, String email, WorkspaceRole role, UUID inviterId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        // Require WORKSPACE_ADMIN
        WorkspaceMembership inviterMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, inviterId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this workspace."));
        if (inviterMembership.getRole() != WorkspaceRole.WORKSPACE_ADMIN) {
            throw new AccessDeniedException("Only Workspace Administrators can invite team members.");
        }

        String normalizedEmail = email.toLowerCase().trim();

        // Check if user is already a member
        Optional<User> existingUserOpt = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail);
        if (existingUserOpt.isPresent()) {
            boolean alreadyMember = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, existingUserOpt.get().getId()).isPresent();
            if (alreadyMember) {
                throw new ValidationException("User " + normalizedEmail + " is already an active member of this workspace.");
            }
        }

        // Revoke any previous pending invitation for this email in this workspace
        Optional<WorkspaceInvitation> existingPending = invitationRepository.findByWorkspaceIdAndEmailAndStatus(
                workspaceId, normalizedEmail, InvitationStatus.PENDING
        );
        existingPending.ifPresent(inv -> {
            inv.setStatus(InvitationStatus.REVOKED);
            invitationRepository.save(inv);
        });

        // Expiration: 7 days
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        String token = generateSecureToken();
        WorkspaceRole assignedRole = role != null ? role : WorkspaceRole.VIEWER;

        WorkspaceInvitation invitation = new WorkspaceInvitation(
                workspaceId,
                normalizedEmail,
                assignedRole,
                token,
                inviterId,
                expiresAt
        );
        invitation = invitationRepository.save(invitation);

        auditService.logAsync(workspaceId, inviterId, "INVITATION_CREATED", "WORKSPACE_INVITATION", invitation.getId().toString(),
                "Generated invitation for " + normalizedEmail + " with role: " + assignedRole, null, "SUCCESS");

        return toDto(invitation, workspace.getName());
    }

    @Transactional(readOnly = true)
    public WorkspaceInvitationDto validateInvitationToken(String token) {
        WorkspaceInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation token is invalid or does not exist."));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ValidationException("This invitation is no longer active (Status: " + invitation.getStatus() + ").");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            throw new ValidationException("This invitation has expired.");
        }

        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(invitation.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace no longer exists."));

        return toDto(invitation, workspace.getName());
    }

    @Transactional
    public void acceptInvitation(String token, UUID userId) {
        WorkspaceInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation token is invalid or does not exist."));

        if (!invitation.isPending()) {
            throw new ValidationException("This invitation is no longer valid.");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(invitation.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", invitation.getWorkspaceId()));

        // Add or update workspace membership with the authorized role
        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .orElseGet(() -> new WorkspaceMembership(workspace, user, invitation.getRole()));

        membership.setRole(invitation.getRole());
        membershipRepository.save(membership);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        auditService.logAsync(workspace.getId(), user.getId(), "INVITATION_ACCEPTED", "WORKSPACE_INVITATION", invitation.getId().toString(),
                user.getEmail() + " joined workspace as " + invitation.getRole(), null, "SUCCESS");
    }

    @Transactional
    public void revokeInvitation(UUID workspaceId, UUID invitationId, UUID userId) {
        WorkspaceMembership inviterMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this workspace."));
        if (inviterMembership.getRole() != WorkspaceRole.WORKSPACE_ADMIN) {
            throw new AccessDeniedException("Only Workspace Administrators can revoke invitations.");
        }

        WorkspaceInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));

        if (!invitation.getWorkspaceId().equals(workspaceId)) {
            throw new AccessDeniedException("Invitation does not belong to this workspace.");
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);

        auditService.logAsync(workspaceId, userId, "INVITATION_REVOKED", "WORKSPACE_INVITATION", invitationId.toString(),
                "Revoked invitation for " + invitation.getEmail(), null, "SUCCESS");
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationDto> getWorkspaceInvitations(UUID workspaceId, UUID userId) {
        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this workspace."));
        if (membership.getRole() != WorkspaceRole.WORKSPACE_ADMIN) {
            throw new AccessDeniedException("Only Workspace Administrators can view invitations.");
        }

        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        return invitationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(inv -> toDto(inv, workspace.getName()))
                .collect(Collectors.toList());
    }

    private WorkspaceInvitationDto toDto(WorkspaceInvitation inv, String workspaceName) {
        return new WorkspaceInvitationDto(
                inv.getId(),
                inv.getWorkspaceId(),
                workspaceName,
                inv.getEmail(),
                inv.getRole(),
                inv.getToken(),
                inv.isExpired() && inv.getStatus() == InvitationStatus.PENDING ? InvitationStatus.EXPIRED : inv.getStatus(),
                inv.getExpiresAt(),
                inv.getCreatedAt()
        );
    }
}
