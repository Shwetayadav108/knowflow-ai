package com.knowflow.ai.admin;

import com.knowflow.ai.audit.AuditLog;
import com.knowflow.ai.auth.UserDto;
import com.knowflow.ai.common.ApiResponse;
import com.knowflow.ai.common.PageResponse;
import com.knowflow.ai.user.GlobalRole;
import com.knowflow.ai.user.UserStatus;
import com.knowflow.ai.workspace.Workspace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminSystemStatsDto>> getSystemStats() {
        AdminSystemStatsDto stats = adminService.getSystemStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        List<UserDto> users = adminService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<ApiResponse<UserDto>> updateUserRole(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body
    ) {
        GlobalRole newRole = GlobalRole.valueOf(body.get("role"));
        UserDto user = adminService.updateUserRole(userId, newRole);
        return ResponseEntity.ok(ApiResponse.success("User role updated", user));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<UserDto>> updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body
    ) {
        UserStatus newStatus = UserStatus.valueOf(body.get("status"));
        UserDto user = adminService.updateUserStatus(userId, newStatus);
        return ResponseEntity.ok(ApiResponse.success("User status updated", user));
    }

    @GetMapping("/workspaces")
    public ResponseEntity<ApiResponse<List<Workspace>>> getAllWorkspaces() {
        List<Workspace> workspaces = adminService.getAllWorkspaces();
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> getAuditLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<AuditLog> logs = adminService.getGlobalAuditLogs(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(logs)));
    }
}
