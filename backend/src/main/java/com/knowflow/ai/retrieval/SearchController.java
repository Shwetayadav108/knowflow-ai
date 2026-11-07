package com.knowflow.ai.retrieval;

import com.knowflow.ai.auth.CustomUserDetails;
import com.knowflow.ai.common.ApiResponse;
import com.knowflow.ai.workspace.WorkspaceRole;
import com.knowflow.ai.workspace.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/search")
public class SearchController {

    private final RetrievalService retrievalService;
    private final WorkspaceService workspaceService;

    public SearchController(RetrievalService retrievalService, WorkspaceService workspaceService) {
        this.retrievalService = retrievalService;
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<RetrievedPassage>>> search(
            @PathVariable UUID workspaceId,
            @RequestBody SearchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        workspaceService.verifyMembership(workspaceId, userDetails.getId());
        WorkspaceRole role = workspaceService.getUserRole(workspaceId, userDetails.getId());

        RetrievalFilter filter = new RetrievalFilter(workspaceId, userDetails.getId(), role);
        filter.setCategory(request.getCategory());
        filter.setDocumentFilterId(request.getDocumentId());
        filter.setTopK(request.getTopK() != null ? request.getTopK() : 10);
        filter.setSimilarityThreshold(request.getSimilarityThreshold() != null ? request.getSimilarityThreshold() : 0.50);

        List<RetrievedPassage> passages = retrievalService.retrieveAuthorizedContext(request.getQuery(), filter);
        return ResponseEntity.ok(ApiResponse.success(passages));
    }
}
