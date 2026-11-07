package com.knowflow.ai.document;

import java.time.Instant;
import java.util.UUID;

public class DocumentDto {
    private UUID id;
    private UUID workspaceId;
    private String title;
    private String originalFilename;
    private FileType fileType;
    private String mimeType;
    private long fileSizeBytes;
    private DocumentStatus status;
    private int version;
    private String description;
    private String folderPath;
    private String category;
    private String tags;
    private DocumentAccessLevel accessLevel;
    private String allowedRoles;
    private String errorMessage;
    private int totalPages;
    private int totalChunks;
    private UUID uploadedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public DocumentDto() {
    }

    public static DocumentDto from(Document doc) {
        DocumentDto dto = new DocumentDto();
        dto.setId(doc.getId());
        dto.setWorkspaceId(doc.getWorkspaceId());
        dto.setTitle(doc.getTitle());
        dto.setOriginalFilename(doc.getOriginalFilename());
        dto.setFileType(doc.getFileType());
        dto.setMimeType(doc.getMimeType());
        dto.setFileSizeBytes(doc.getFileSizeBytes());
        dto.setStatus(doc.getStatus());
        dto.setVersion(doc.getVersion());
        dto.setDescription(doc.getDescription());
        dto.setFolderPath(doc.getFolderPath());
        dto.setCategory(doc.getCategory());
        dto.setTags(doc.getTags());
        dto.setAccessLevel(doc.getAccessLevel());
        dto.setAllowedRoles(doc.getAllowedRoles());
        dto.setErrorMessage(doc.getErrorMessage());
        dto.setTotalPages(doc.getTotalPages());
        dto.setTotalChunks(doc.getTotalChunks());
        dto.setUploadedBy(doc.getUploadedBy());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setUpdatedAt(doc.getUpdatedAt());
        return dto;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public DocumentAccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(DocumentAccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
