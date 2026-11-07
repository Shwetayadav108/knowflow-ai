package com.knowflow.ai.workspace;

import com.knowflow.ai.auth.CustomUserDetails;
import com.knowflow.ai.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<WorkspaceInvitationDto>> createInvitation(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WorkspaceInvitationDto invitation = invitationService.createInvitation(
                workspaceId, request.getEmail(), request.getRole(), userDetails.getId()
        );
        return ResponseEntity.ok(ApiResponse.success("Invitation created successfully", invitation));
    }

    @GetMapping("/workspaces/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationDto>>> getWorkspaceInvitations(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<WorkspaceInvitationDto> list = invitationService.getWorkspaceInvitations(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @DeleteMapping("/workspaces/{workspaceId}/invitations/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        invitationService.revokeInvitation(workspaceId, invitationId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Invitation revoked successfully", null));
    }

    @GetMapping("/invitations/{token}")
    public ResponseEntity<ApiResponse<WorkspaceInvitationDto>> validateInvitationToken(@PathVariable String token) {
        WorkspaceInvitationDto dto = invitationService.validateInvitationToken(token);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @PathVariable String token,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        invitationService.acceptInvitation(token, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Invitation accepted successfully. You have joined the workspace.", null));
    }
}
