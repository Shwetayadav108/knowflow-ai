package com.knowflow.ai.document;

import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.AccessDeniedException;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import com.knowflow.ai.common.exception.ValidationException;
import com.knowflow.ai.ingestion.IngestionWorker;
import com.knowflow.ai.security.DocumentAuthorizationService;
import com.knowflow.ai.storage.StorageService;
import com.knowflow.ai.workspace.WorkspaceRole;
import com.knowflow.ai.workspace.WorkspaceService;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StorageService storageService;
    private final WorkspaceService workspaceService;
    private final DocumentAuthorizationService authService;
    private final IngestionWorker ingestionWorker;
    private final AuditService auditService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            StorageService storageService,
            WorkspaceService workspaceService,
            DocumentAuthorizationService authService,
            IngestionWorker ingestionWorker,
            AuditService auditService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
        this.workspaceService = workspaceService;
        this.authService = authService;
        this.ingestionWorker = ingestionWorker;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentDto uploadDocument(
            UUID workspaceId,
            MultipartFile file,
            String title,
            String category,
            String folderPath,
            DocumentAccessLevel accessLevel,
            String allowedRoles,
            UUID userId
    ) {
        workspaceService.requireRole(workspaceId, userId, WorkspaceRole.EDITOR);

        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded file cannot be empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ValidationException("Filename is missing.");
        }

        // Duplicate detection
        if (documentRepository.existsByWorkspaceIdAndOriginalFilenameAndFileSizeBytesAndDeletedAtIsNull(workspaceId, originalFilename, file.getSize())) {
            throw new ValidationException("A document with the same filename ('" + originalFilename + "') and size already exists in this workspace.");
        }

        // Validate extension
        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        FileType fileType;
        switch (extension) {
            case "pdf":
                fileType = FileType.PDF;
                break;
            case "docx":
                fileType = FileType.DOCX;
                break;
            case "txt":
                fileType = FileType.TXT;
                break;
            case "csv":
                fileType = FileType.CSV;
                break;
            case "pptx":
                fileType = FileType.PPTX;
                break;
            default:
                throw new ValidationException("Unsupported file type: ." + extension + ". Allowed formats: PDF, DOCX, TXT, CSV, PPTX.");
        }

        String docTitle = (title != null && !title.isBlank()) ? title.trim() : FilenameUtils.getBaseName(originalFilename);
        String docCategory = (category != null && !category.isBlank()) ? category.trim() : "General";
        String docFolder = (folderPath != null && !folderPath.isBlank()) ? folderPath.trim() : "/";
        DocumentAccessLevel docAccessLevel = accessLevel != null ? accessLevel : DocumentAccessLevel.WORKSPACE_SHARED;

        try {
            String storagePath = storageService.store(file.getInputStream(), originalFilename, workspaceId);

            Document document = new Document(
                    workspaceId,
                    docTitle,
                    originalFilename,
                    fileType,
                    file.getSize(),
                    storagePath,
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                    userId
            );
            document.setCategory(docCategory);
            document.setFolderPath(docFolder);
            document.setAccessLevel(docAccessLevel);
            document.setAllowedRoles(allowedRoles);

            document = documentRepository.save(document);

            // Enqueue asynchronous document processing pipeline
            ingestionWorker.enqueueDocument(document.getId());

            auditService.logAsync(workspaceId, userId, "DOCUMENT_UPLOADED", "DOCUMENT", document.getId().toString(),
                    "Uploaded document: " + docTitle + " (" + originalFilename + ")", null, "SUCCESS");

            return DocumentDto.from(document);

        } catch (IOException e) {
            log.error("Failed to read file input stream: {}", e.getMessage());
            throw new ValidationException("Failed to process uploaded file: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> getDocuments(
            UUID workspaceId,
            String category,
            DocumentStatus status,
            String search,
            Pageable pageable,
            UUID userId
    ) {
        WorkspaceRole userRole = workspaceService.getUserRole(workspaceId, userId);
        if (userRole == null) {
            throw new AccessDeniedException("Access denied: You are not a member of this workspace.");
        }
        boolean isWsAdmin = (userRole == WorkspaceRole.WORKSPACE_ADMIN);

        Page<Document> page = documentRepository.findAuthorizedDocuments(
                workspaceId, userId, userRole.name(), isWsAdmin, category, status, search, pageable
        );
        return page.map(DocumentDto::from);
    }

    @Transactional(readOnly = true)
    public DocumentDto getDocument(UUID documentId, UUID userId) {
        if (!authService.canViewDocument(documentId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to view this document.");
        }

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        return DocumentDto.from(document);
    }

    @Transactional(readOnly = true)
    public DocumentPreviewDto getDocumentPreview(UUID documentId, UUID userId) {
        if (!authService.canViewDocument(documentId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to preview this document.");
        }

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<ChunkPreviewDto> chunkDtos = chunks.stream()
                .map(c -> new ChunkPreviewDto(
                        c.getId(),
                        c.getChunkIndex(),
                        c.getPageNumber(),
                        c.getSectionHeading(),
                        c.getContent(),
                        c.getTokenCount()
                ))
                .collect(Collectors.toList());

        return new DocumentPreviewDto(DocumentDto.from(document), chunkDtos);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID documentId, UUID userId) {
        if (!authService.canViewDocument(documentId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to download this document.");
        }

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        auditService.logAsync(document.getWorkspaceId(), userId, "DOCUMENT_DOWNLOADED", "DOCUMENT", documentId.toString(),
                "Downloaded document: " + document.getTitle(), null, "SUCCESS");

        return storageService.loadAsResource(document.getStoragePath());
    }

    @Transactional
    public DocumentDto updateDocument(UUID documentId, UpdateDocumentRequest request, UUID userId) {
        if (!authService.canEditOrDeleteDocument(documentId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to modify this document.");
        }

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            document.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            document.setDescription(request.getDescription().trim());
        }
        if (request.getFolderPath() != null) {
            document.setFolderPath(request.getFolderPath().trim());
        }
        if (request.getCategory() != null) {
            document.setCategory(request.getCategory().trim());
        }
        if (request.getTags() != null) {
            document.setTags(request.getTags().trim());
        }
        if (request.getAccessLevel() != null) {
            document.setAccessLevel(request.getAccessLevel());
        }
        if (request.getAllowedRoles() != null) {
            document.setAllowedRoles(request.getAllowedRoles().trim());
        }

        document = documentRepository.save(document);
        auditService.logAsync(document.getWorkspaceId(), userId, "DOCUMENT_UPDATED", "DOCUMENT", documentId.toString(),
                "Updated metadata for: " + document.getTitle(), null, "SUCCESS");

        return DocumentDto.from(document);
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        if (!authService.canEditOrDeleteDocument(documentId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to delete this document.");
        }

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        document.setDeletedAt(Instant.now());
        documentRepository.save(document);

        // Delete chunks to reclaim search index
        chunkRepository.deleteByDocumentId(documentId);

        auditService.logAsync(document.getWorkspaceId(), userId, "DOCUMENT_DELETED", "DOCUMENT", documentId.toString(),
                "Deleted document: " + document.getTitle(), null, "SUCCESS");
    }

    @Transactional
    public void reprocessDocument(UUID documentId, UUID userId) {
        if (!authService.canEditOrDeleteDocument(documentId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to reprocess this document.");
        }

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        document.setStatus(DocumentStatus.UPLOADED);
        document.setErrorMessage(null);
        documentRepository.save(document);

        ingestionWorker.enqueueDocument(documentId);
        auditService.logAsync(document.getWorkspaceId(), userId, "DOCUMENT_REPROCESSED", "DOCUMENT", documentId.toString(),
                "Triggered reprocessing for: " + document.getTitle(), null, "SUCCESS");
    }

    @Transactional(readOnly = true)
    public List<String> getCategories(UUID workspaceId, UUID userId) {
        WorkspaceRole userRole = workspaceService.getUserRole(workspaceId, userId);
        if (userRole == null) {
            throw new AccessDeniedException("Access denied: You are not a member of this workspace.");
        }
        boolean isWsAdmin = (userRole == WorkspaceRole.WORKSPACE_ADMIN);
        return documentRepository.findAuthorizedCategories(workspaceId, userId, userRole.name(), isWsAdmin);
    }

    @Transactional(readOnly = true)
    public List<String> getFolders(UUID workspaceId, UUID userId) {
        WorkspaceRole userRole = workspaceService.getUserRole(workspaceId, userId);
        if (userRole == null) {
            throw new AccessDeniedException("Access denied: You are not a member of this workspace.");
        }
        boolean isWsAdmin = (userRole == WorkspaceRole.WORKSPACE_ADMIN);
        return documentRepository.findAuthorizedFolders(workspaceId, userId, userRole.name(), isWsAdmin);
    }
}
