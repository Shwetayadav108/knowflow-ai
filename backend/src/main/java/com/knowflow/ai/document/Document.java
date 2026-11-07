package com.knowflow.ai.document;

import com.knowflow.ai.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(nullable = false)
    private int version = 1;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "folder_path", nullable = false)
    private String folderPath = "/";

    @Column(length = 100)
    private String category = "General";

    @Column(columnDefinition = "text")
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private DocumentAccessLevel accessLevel = DocumentAccessLevel.WORKSPACE_SHARED;

    @Column(name = "allowed_roles")
    private String allowedRoles; // comma separated: WORKSPACE_ADMIN,EDITOR

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "total_pages")
    private int totalPages = 1;

    @Column(name = "total_chunks")
    private int totalChunks = 0;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Document() {
    }

    public Document(UUID workspaceId, String title, String originalFilename, FileType fileType,
                    long fileSizeBytes, String storagePath, String mimeType, UUID uploadedBy) {
        this.workspaceId = workspaceId;
        this.title = title;
        this.originalFilename = originalFilename;
        this.fileType = fileType;
        this.fileSizeBytes = fileSizeBytes;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.uploadedBy = uploadedBy;
        this.status = DocumentStatus.UPLOADED;
        this.version = 1;
        this.folderPath = "/";
        this.category = "General";
        this.accessLevel = DocumentAccessLevel.WORKSPACE_SHARED;
    }

    // Getters and Setters
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

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
