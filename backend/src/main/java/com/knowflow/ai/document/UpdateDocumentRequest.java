package com.knowflow.ai.document;

public class UpdateDocumentRequest {
    private String title;
    private String description;
    private String folderPath;
    private String category;
    private String tags;
    private DocumentAccessLevel accessLevel;
    private String allowedRoles;

    public UpdateDocumentRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
}
