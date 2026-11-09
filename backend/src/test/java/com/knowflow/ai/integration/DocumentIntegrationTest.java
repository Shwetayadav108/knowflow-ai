package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.document.Document;
import com.knowflow.ai.document.DocumentAccessLevel;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.workspace.CreateWorkspaceRequest;
import com.knowflow.ai.workspace.InviteMemberRequest;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class DocumentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    private String registerAndGetToken(String name, String email) throws Exception {
        RegisterRequest reg = new RegisterRequest(name, email, "Password123!");
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.path("data").path("token").asText();
    }

    private String createWorkspace(String token, String name) throws Exception {
        CreateWorkspaceRequest req = new CreateWorkspaceRequest(name, "Workspace description");
        MvcResult res = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String uploadTestDocument(String token, String workspaceId, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/workspaces/" + workspaceId + "/documents/upload")
                        .file(file)
                        .param("title", filename)
                        .param("category", "General")
                        .param("folderPath", "/")
                        .param("accessLevel", DocumentAccessLevel.WORKSPACE_SHARED.name())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode docJson = objectMapper.readTree(uploadRes.getResponse().getContentAsString());
        return docJson.path("data").path("id").asText();
    }

    @Test
    @DisplayName("Upload document, fetch metadata, download, and list")
    public void testDocumentUploadAndLifecycle() throws Exception {
        String token = registerAndGetToken("Doc Tester", "doctest-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "Engineering Documents");

        // 1. Upload text document
        String docId = uploadTestDocument(token, workspaceId, "Corporate_Policy.txt",
                "KnowFlow AI Enterprise Policy Document.\nEmployees are granted 25 days leave.");

        // 2. Fetch categories
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 3. Fetch folders
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents/folders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 4. Download document
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents/" + docId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 5. Paginated document listing
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Soft-delete marks document deleted_at and preserves physical file on disk")
    public void testSoftDeletePreservesPhysicalFile() throws Exception {
        String token = registerAndGetToken("Soft Delete Tester", "softdel-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "Soft Delete WS");

        String docId = uploadTestDocument(token, workspaceId, "SoftDeleteTest.txt", "Content to be soft deleted");
        UUID docUuid = UUID.fromString(docId);

        Document doc = documentRepository.findById(docUuid).orElseThrow();
        Path physicalPath = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalPath)).isTrue();

        // Perform soft delete
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document deleted successfully"));

        // File must STILL exist on disk (soft deletion preserves file)
        assertThat(Files.exists(physicalPath)).isTrue();

        // Database record must still exist with deletedAt != null
        Optional<Document> afterSoftDelete = documentRepository.findById(docUuid);
        assertThat(afterSoftDelete).isPresent();
        assertThat(afterSoftDelete.get().getDeletedAt()).isNotNull();

        // Must not be visible in active document queries
        assertThat(documentRepository.findByIdAndDeletedAtIsNull(docUuid)).isEmpty();
    }

    @Test
    @DisplayName("Permanent delete cleans up physical file on disk and removes database entity")
    public void testPermanentDeleteCleansUpPhysicalFileAndEntity() throws Exception {
        String token = registerAndGetToken("Hard Delete Tester", "harddel-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "Hard Delete WS");

        String docId = uploadTestDocument(token, workspaceId, "PermanentDeleteTest.txt", "Content to be permanently deleted");
        UUID docUuid = UUID.fromString(docId);

        Document doc = documentRepository.findById(docUuid).orElseThrow();
        Path physicalPath = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalPath)).isTrue();

        // Perform permanent delete via ?permanent=true
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + docId + "?permanent=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document permanently deleted"));

        // Physical file must be DELETED from disk
        assertThat(Files.exists(physicalPath)).isFalse();

        // Database record must be completely deleted
        assertThat(documentRepository.findById(docUuid)).isEmpty();
    }

    @Test
    @DisplayName("Permanent delete endpoint /permanent also cleans up physical file on disk")
    public void testPermanentDeleteEndpointCleansUpPhysicalFile() throws Exception {
        String token = registerAndGetToken("Permanent Endpoint Tester", "permdel-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "Permanent Endpoint WS");

        String docId = uploadTestDocument(token, workspaceId, "PermanentEndpointTest.txt", "Content for /permanent endpoint");
        UUID docUuid = UUID.fromString(docId);

        Document doc = documentRepository.findById(docUuid).orElseThrow();
        Path physicalPath = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalPath)).isTrue();

        // Perform permanent delete via /permanent
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + docId + "/permanent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document permanently deleted"));

        // Physical file must be DELETED from disk
        assertThat(Files.exists(physicalPath)).isFalse();

        // Database record must be completely deleted
        assertThat(documentRepository.findById(docUuid)).isEmpty();
    }

    @Test
    @DisplayName("Viewer member cannot permanently delete document and physical file is preserved")
    public void testViewerForbiddenFromPermanentDeletion() throws Exception {
        String adminToken = registerAndGetToken("WS Admin", "wsadmin-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(adminToken, "Admin WS");

        String docId = uploadTestDocument(adminToken, workspaceId, "ProtectedDoc.txt", "Protected content");
        UUID docUuid = UUID.fromString(docId);

        Document doc = documentRepository.findById(docUuid).orElseThrow();
        Path physicalPath = Path.of(doc.getStoragePath());
        assertThat(Files.exists(physicalPath)).isTrue();

        // Register and invite viewer
        String viewerEmail = "viewer-" + UUID.randomUUID() + "@enterprise.io";
        String viewerToken = registerAndGetToken("Viewer User", viewerEmail);

        InviteMemberRequest inviteReq = new InviteMemberRequest(viewerEmail, WorkspaceRole.VIEWER);
        MvcResult inviteRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isOk())
                .andReturn();

        String inviteToken = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).path("data").path("token").asText();
        mockMvc.perform(post("/api/v1/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // Viewer attempts permanent delete via ?permanent=true -> 403 Forbidden!
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + docId + "?permanent=true")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // Viewer attempts permanent delete via /permanent -> 403 Forbidden!
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + docId + "/permanent")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // Physical file and DB record must STILL exist!
        assertThat(Files.exists(physicalPath)).isTrue();
        assertThat(documentRepository.findById(docUuid)).isPresent();
    }

    @Test
    @DisplayName("Cross-workspace permanent deletion is forbidden and physical file is preserved")
    public void testCrossWorkspacePermanentDeletionRejected() throws Exception {
        String tokenA = registerAndGetToken("User A", "usera-" + UUID.randomUUID() + "@enterprise.io");
        String wsA = createWorkspace(tokenA, "Workspace A");

        String tokenB = registerAndGetToken("User B", "userb-" + UUID.randomUUID() + "@enterprise.io");
        String wsB = createWorkspace(tokenB, "Workspace B");

        String docAId = uploadTestDocument(tokenA, wsA, "DocA.txt", "Workspace A Document Content");
        UUID docAUuid = UUID.fromString(docAId);

        Document docA = documentRepository.findById(docAUuid).orElseThrow();
        Path physicalPath = Path.of(docA.getStoragePath());
        assertThat(Files.exists(physicalPath)).isTrue();

        // User A attempts to target wsB for docA -> 403 Forbidden!
        mockMvc.perform(delete("/api/v1/workspaces/" + wsB + "/documents/" + docAId + "/permanent")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        // File and DB record must remain untouched
        assertThat(Files.exists(physicalPath)).isTrue();
        assertThat(documentRepository.findById(docAUuid)).isPresent();
    }

    @Test
    @DisplayName("Unauthenticated request cannot permanently delete document")
    public void testUnauthenticatedPermanentDeleteRejected() throws Exception {
        String token = registerAndGetToken("Anon Target", "anontarget-" + UUID.randomUUID() + "@enterprise.io");
        String wsId = createWorkspace(token, "Anon Target WS");
        String docId = uploadTestDocument(token, wsId, "AnonDoc.txt", "Content");

        mockMvc.perform(delete("/api/v1/workspaces/" + wsId + "/documents/" + docId + "/permanent"))
                .andExpect(status().isForbidden());
    }
}
