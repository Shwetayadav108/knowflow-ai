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
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceDto>>> getUserWorkspaces(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<WorkspaceDto> workspaces = workspaceService.getUserWorkspaces(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceDto>> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WorkspaceDto workspace = workspaceService.createWorkspace(request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Workspace created successfully", workspace));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceDto>> getWorkspace(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WorkspaceDto workspace = workspaceService.getWorkspace(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    @PutMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceDto>> updateWorkspace(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WorkspaceDto workspace = workspaceService.updateWorkspace(workspaceId, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Workspace updated successfully", workspace));
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkspace(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        workspaceService.deleteWorkspace(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Workspace deleted successfully", null));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<ApiResponse<List<WorkspaceMemberDto>>> getMembers(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<WorkspaceMemberDto> members = workspaceService.getMembers(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        workspaceService.removeMember(workspaceId, memberId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Member removed from workspace", null));
    }
}
