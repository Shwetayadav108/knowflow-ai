package com.knowflow.ai.security;

import com.knowflow.ai.document.Document;
import com.knowflow.ai.document.DocumentAccessLevel;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.workspace.WorkspaceMembership;
import com.knowflow.ai.workspace.WorkspaceMembershipRepository;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentAuthorizationService {

    private final DocumentRepository documentRepository;
    private final WorkspaceMembershipRepository membershipRepository;

    public DocumentAuthorizationService(
            DocumentRepository documentRepository,
            WorkspaceMembershipRepository membershipRepository
    ) {
        this.documentRepository = documentRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public boolean canViewDocument(UUID documentId, UUID userId) {
        Optional<Document> docOpt = documentRepository.findByIdAndDeletedAtIsNull(documentId);
        if (docOpt.isEmpty()) return false;
        Document doc = docOpt.get();

        Optional<WorkspaceMembership> memOpt = membershipRepository.findByWorkspaceIdAndUserId(doc.getWorkspaceId(), userId);
        if (memOpt.isEmpty()) return false;
        WorkspaceMembership mem = memOpt.get();

        if (mem.getRole() == WorkspaceRole.WORKSPACE_ADMIN) {
            return true;
        }

        if (doc.getAccessLevel() == DocumentAccessLevel.WORKSPACE_SHARED) {
            return true;
        }

        if (doc.getAccessLevel() == DocumentAccessLevel.PRIVATE) {
            return doc.getUploadedBy() != null && doc.getUploadedBy().equals(userId);
        }

        if (doc.getAccessLevel() == DocumentAccessLevel.RESTRICTED_ROLES && doc.getAllowedRoles() != null) {
            String[] roles = doc.getAllowedRoles().split(",");
            for (String r : roles) {
                if (r.trim().equalsIgnoreCase(mem.getRole().name())) {
                    return true;
                }
            }
        }

        return false;
    }

    @Transactional(readOnly = true)
    public boolean canEditOrDeleteDocument(UUID documentId, UUID userId) {
        Optional<Document> docOpt = documentRepository.findByIdAndDeletedAtIsNull(documentId);
        if (docOpt.isEmpty()) return false;
        return canEditOrDeleteDocument(docOpt.get(), userId);
    }

    @Transactional(readOnly = true)
    public boolean canEditOrDeleteDocument(Document doc, UUID userId) {
        if (doc == null || userId == null) return false;

        Optional<WorkspaceMembership> memOpt = membershipRepository.findByWorkspaceIdAndUserId(doc.getWorkspaceId(), userId);
        if (memOpt.isEmpty()) return false;
        WorkspaceMembership mem = memOpt.get();

        // Workspace admins can manage all docs
        if (mem.getRole() == WorkspaceRole.WORKSPACE_ADMIN) {
            return true;
        }

        // Uploader can manage their own docs if editor
        if (mem.getRole() == WorkspaceRole.EDITOR && doc.getUploadedBy() != null && doc.getUploadedBy().equals(userId)) {
            return true;
        }

        return false;
    }
}
