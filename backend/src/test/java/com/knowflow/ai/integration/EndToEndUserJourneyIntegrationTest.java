package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.ForgotPasswordRequest;
import com.knowflow.ai.auth.LoginRequest;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.auth.ResetPasswordRequest;
import com.knowflow.ai.chat.SendMessageRequest;
import com.knowflow.ai.chat.SubmitFeedbackRequest;
import com.knowflow.ai.document.Document;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.email.EmailService;
import com.knowflow.ai.retrieval.SearchRequest;
import com.knowflow.ai.workspace.CreateWorkspaceRequest;
import com.knowflow.ai.workspace.InviteMemberRequest;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class EndToEndUserJourneyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @SpyBean
    private EmailService emailService;

    private JsonNode registerUser(String name, String email, String password) throws Exception {
        RegisterRequest reg = new RegisterRequest(name, email, password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
    }

    private String loginUser(String email, String password) throws Exception {
        LoginRequest login = new LoginRequest(email, password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String createWorkspace(String token, String name) throws Exception {
        CreateWorkspaceRequest req = new CreateWorkspaceRequest(name, "Workspace description");
        MvcResult res = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private void inviteAndAccept(String adminToken, String workspaceId, String inviteeEmail, String inviteeToken, WorkspaceRole role) throws Exception {
        InviteMemberRequest inviteReq = new InviteMemberRequest(inviteeEmail, role);
        MvcResult inviteRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isOk())
                .andReturn();

        String inviteToken = objectMapper.readTree(inviteRes.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(post("/api/v1/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk());
    }

    private String uploadDocument(String token, String workspaceId, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult res = mockMvc.perform(multipart("/api/v1/workspaces/" + workspaceId + "/documents/upload")
                        .file(file)
                        .param("title", filename)
                        .param("category", "Engineering")
                        .param("folderPath", "/docs")
                        .param("accessLevel", "WORKSPACE_SHARED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    @Test
    @DisplayName("Complete End-to-End User Journey: Flows 1 through 10")
    public void testCompleteEndToEndUserJourney() throws Exception {
        // =====================================================================
        // FLOW 1: User Registration and Login
        // =====================================================================
        String aliceEmail = "alice.admin-" + UUID.randomUUID() + "@enterprise.io";
        String aliceInitialPw = "SecurePassword123!";
        JsonNode aliceRegData = registerUser("Alice Admin", aliceEmail, aliceInitialPw);
        String aliceAccessToken = aliceRegData.path("token").asText();
        String aliceRefreshToken = aliceRegData.path("refreshToken").asText();
        assertThat(aliceAccessToken).isNotEmpty();
        assertThat(aliceRefreshToken).isNotEmpty();

        // Login with credentials
        String aliceLoginToken = loginUser(aliceEmail, aliceInitialPw);
        assertThat(aliceLoginToken).isNotEmpty();

        // Get authenticated user info
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + aliceAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(aliceEmail));

        // =====================================================================
        // FLOW 2: JWT Access and Refresh Token Flow
        // =====================================================================
        // Refresh token yields a new token pair
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", aliceRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshData = objectMapper.readTree(refreshRes.getResponse().getContentAsString()).path("data");
        String aliceNewAccessToken = refreshData.path("token").asText();
        String aliceNewRefreshToken = refreshData.path("refreshToken").asText();

        // Access token submitted to refresh endpoint must fail with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", aliceNewAccessToken))))
                .andExpect(status().isUnauthorized());

        // Refresh token submitted as Bearer token to API must fail with 401
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + aliceNewRefreshToken))
                .andExpect(status().isUnauthorized());

        // =====================================================================
        // FLOW 3: Workspace Creation and Membership
        // =====================================================================
        String workspaceId = createWorkspace(aliceNewAccessToken, "Enterprise Knowledge Hub");

        // Register Bob (Editor) and Charlie (Viewer)
        String bobEmail = "bob.editor-" + UUID.randomUUID() + "@enterprise.io";
        String bobToken = registerUser("Bob Editor", bobEmail, "SecurePassword123!").path("token").asText();

        String charlieEmail = "charlie.viewer-" + UUID.randomUUID() + "@enterprise.io";
        String charlieToken = registerUser("Charlie Viewer", charlieEmail, "SecurePassword123!").path("token").asText();

        // Invite Bob as EDITOR and Charlie as VIEWER
        inviteAndAccept(aliceNewAccessToken, workspaceId, bobEmail, bobToken, WorkspaceRole.EDITOR);
        inviteAndAccept(aliceNewAccessToken, workspaceId, charlieEmail, charlieToken, WorkspaceRole.VIEWER);

        // Verify members list contains 3 members
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + aliceNewAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        // =====================================================================
        // FLOW 4: Document Upload and Ingestion
        // =====================================================================
        // Alice (Admin) uploads policy handbook
        String doc1Content = "KnowFlow AI Company Policy Handbook.\nEmployees are granted 25 vacation days per year.\nStandard working hours are 9:00 AM to 5:00 PM Monday through Friday.\nRemote working is supported up to two days weekly.";
        String doc1Id = uploadDocument(aliceNewAccessToken, workspaceId, "Company_Handbook.txt", doc1Content);
        assertThat(doc1Id).isNotEmpty();

        // Bob (Editor) uploads technical specs
        String doc2Content = "KnowFlow AI Technical Architecture Specification.\nBackend services run on Spring Boot 3 with Java 21.\nPostgreSQL pgvector stores 768-dimensional document embeddings.";
        String doc2Id = uploadDocument(bobToken, workspaceId, "Tech_Architecture.txt", doc2Content);
        assertThat(doc2Id).isNotEmpty();

        // Charlie (Viewer) attempts upload -> 403 Forbidden!
        MockMultipartFile viewerFile = new MockMultipartFile("file", "Viewer_Doc.txt", "text/plain", "Viewer content".getBytes());
        mockMvc.perform(multipart("/api/v1/workspaces/" + workspaceId + "/documents/upload")
                        .file(viewerFile)
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isForbidden());

        // =====================================================================
        // FLOW 5: Document Listing, Preview and Download
        // =====================================================================
        // List documents
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents")
                        .header("Authorization", "Bearer " + aliceNewAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // Get categories and folders
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents/categories")
                        .header("Authorization", "Bearer " + aliceNewAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents/folders")
                        .header("Authorization", "Bearer " + aliceNewAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // Document preview
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents/" + doc1Id + "/preview")
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.id").value(doc1Id));

        // Document download
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/documents/" + doc1Id + "/download")
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isOk());

        // =====================================================================
        // FLOW 6: Document Soft Deletion and Permanent Deletion
        // =====================================================================
        // Upload temporary doc for soft-delete test
        String tempDoc1Id = uploadDocument(aliceNewAccessToken, workspaceId, "Temp_Soft.txt", "Temporary soft delete file");
        UUID tempDoc1Uuid = UUID.fromString(tempDoc1Id);
        Document tempDoc1 = documentRepository.findById(tempDoc1Uuid).orElseThrow();
        Path tempDoc1File = Path.of(tempDoc1.getStoragePath());
        assertThat(Files.exists(tempDoc1File)).isTrue();

        // Perform soft delete
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + tempDoc1Id)
                        .header("Authorization", "Bearer " + aliceNewAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document deleted successfully"));

        // Physical file is PRESERVED on disk
        assertThat(Files.exists(tempDoc1File)).isTrue();
        // DB entity marked deletedAt
        assertThat(documentRepository.findById(tempDoc1Uuid).get().getDeletedAt()).isNotNull();

        // Upload temporary doc for permanent delete test
        String tempDoc2Id = uploadDocument(aliceNewAccessToken, workspaceId, "Temp_Hard.txt", "Temporary permanent delete file");
        UUID tempDoc2Uuid = UUID.fromString(tempDoc2Id);
        Document tempDoc2 = documentRepository.findById(tempDoc2Uuid).orElseThrow();
        Path tempDoc2File = Path.of(tempDoc2.getStoragePath());
        assertThat(Files.exists(tempDoc2File)).isTrue();

        // Perform permanent delete via ?permanent=true
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + tempDoc2Id + "?permanent=true")
                        .header("Authorization", "Bearer " + aliceNewAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document permanently deleted"));

        // Physical file is DELETED
        assertThat(Files.exists(tempDoc2File)).isFalse();
        // DB entity is REMOVED
        assertThat(documentRepository.findById(tempDoc2Uuid)).isEmpty();

        // =====================================================================
        // FLOW 7: RAG Question Answering with Citations
        // =====================================================================
        // Create conversation
        MvcResult convRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/conversations")
                        .header("Authorization", "Bearer " + aliceNewAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Policy Questions"))))
                .andExpect(status().isOk())
                .andReturn();
        String convId = objectMapper.readTree(convRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Send question
        SendMessageRequest chatReq = new SendMessageRequest("How many vacation days do employees receive?");
        MvcResult chatRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + aliceNewAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sender").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andReturn();

        String messageId = objectMapper.readTree(chatRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Submit feedback
        SubmitFeedbackRequest fbReq = new SubmitFeedbackRequest(1, "Accurate and prompt response");
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/messages/" + messageId + "/feedback")
                        .header("Authorization", "Bearer " + aliceNewAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fbReq)))
                .andExpect(status().isOk());

        // =====================================================================
        // FLOW 8: Search and Document Retrieval
        // =====================================================================
        SearchRequest searchReq = new SearchRequest();
        searchReq.setQuery("vacation policy");
        searchReq.setTopK(5);
        searchReq.setSimilarityThreshold(0.30);

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/search")
                        .header("Authorization", "Bearer " + aliceNewAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Non-member search rejected with 403
        String strangerToken = registerUser("Stranger", "stranger-" + UUID.randomUUID() + "@enterprise.io", "Pass123!").path("token").asText();
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/search")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchReq)))
                .andExpect(status().isForbidden());

        // =====================================================================
        // FLOW 9: Workspace Role-Based Access Control (RBAC)
        // =====================================================================
        // Charlie (Viewer) cannot permanently delete
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + doc1Id + "/permanent")
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isForbidden());

        // Bob (Editor) cannot delete Alice's document (not uploader)
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + doc1Id + "/permanent")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // Bob (Editor) CAN permanently delete his own document (uploader)
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/documents/" + doc2Id + "/permanent")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        // =====================================================================
        // FLOW 10: Password Reset API Flow (Session Invalidation & Code Security)
        // =====================================================================
        // Alice requests password reset
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(aliceEmail);
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Capture 6-digit reset code
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendPasswordResetEmail(eq(aliceEmail), codeCaptor.capture(), any());
        String resetCode = codeCaptor.getValue();
        assertNotNull(resetCode);
        assertEquals(6, resetCode.length());

        // Invalid code rejected
        String aliceNewPw = "AliceBrandNewPassword999!";
        ResetPasswordRequest badCodeReq = new ResetPasswordRequest(aliceEmail, "000000", aliceNewPw);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCodeReq)))
                .andExpect(status().isBadRequest());

        // Valid reset code succeeds
        ResetPasswordRequest validResetReq = new ResetPasswordRequest(aliceEmail, resetCode, aliceNewPw);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Old password fails login -> 401
        LoginRequest oldPwLogin = new LoginRequest(aliceEmail, aliceInitialPw);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldPwLogin)))
                .andExpect(status().isUnauthorized());

        // New password succeeds login -> 200
        LoginRequest newPwLogin = new LoginRequest(aliceEmail, aliceNewPw);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPwLogin)))
                .andExpect(status().isOk());

        // Old session invalidation: previous access token rejected -> 401
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + aliceNewAccessToken))
                .andExpect(status().isUnauthorized());

        // Old session invalidation: previous refresh token rejected -> 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", aliceNewRefreshToken))))
                .andExpect(status().isUnauthorized());

        // Code re-use rejected (single-use) -> 400
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetReq)))
                .andExpect(status().isBadRequest());
    }
}