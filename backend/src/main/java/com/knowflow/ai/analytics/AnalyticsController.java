package com.knowflow.ai.analytics;

import com.knowflow.ai.auth.CustomUserDetails;
import com.knowflow.ai.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardMetricsDto>> getMetrics(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        DashboardMetricsDto metrics = analyticsService.getWorkspaceMetrics(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }
}
