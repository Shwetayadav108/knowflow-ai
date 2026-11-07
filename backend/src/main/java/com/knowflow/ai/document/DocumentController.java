package com.knowflow.ai.document;

import com.knowflow.ai.auth.CustomUserDetails;
import com.knowflow.ai.common.ApiResponse;
import com.knowflow.ai.common.PageResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentDto>> uploadDocument(
            @PathVariable UUID workspaceId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "folderPath", required = false) String folderPath,
            @RequestParam(value = "accessLevel", required = false) DocumentAccessLevel accessLevel,
            @RequestParam(value = "allowedRoles", required = false) String allowedRoles,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        DocumentDto doc = documentService.uploadDocument(
                workspaceId, file, title, category, folderPath, accessLevel, allowedRoles, userDetails.getId()
        );
        return ResponseEntity.ok(ApiResponse.success("Document uploaded and queued for indexing", doc));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DocumentDto>>> getDocuments(
            @PathVariable UUID workspaceId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "direction", defaultValue = "DESC") String direction,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Page<DocumentDto> docs = documentService.getDocuments(
                workspaceId, category, status, search, PageRequest.of(page, size, sort), userDetails.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(docs)));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentDto>> getDocument(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        DocumentDto doc = documentService.getDocument(documentId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(doc));
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<ApiResponse<DocumentPreviewDto>> getDocumentPreview(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        DocumentPreviewDto preview = documentService.getDocumentPreview(documentId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(preview));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        DocumentDto doc = documentService.getDocument(documentId, userDetails.getId());
        Resource resource = documentService.downloadDocument(documentId, userDetails.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getOriginalFilename() + "\"")
                .body(resource);
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentDto>> updateDocument(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        DocumentDto doc = documentService.updateDocument(documentId, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Document updated successfully", doc));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        documentService.deleteDocument(documentId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    @PostMapping("/{documentId}/reprocess")
    public ResponseEntity<ApiResponse<Void>> reprocessDocument(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        documentService.reprocessDocument(documentId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Reprocessing initiated", null));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<String> categories = documentService.getCategories(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/folders")
    public ResponseEntity<ApiResponse<List<String>>> getFolders(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<String> folders = documentService.getFolders(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(folders));
    }
}
