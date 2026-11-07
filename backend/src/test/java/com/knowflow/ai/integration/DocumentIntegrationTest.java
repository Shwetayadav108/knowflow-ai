package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.document.DocumentAccessLevel;
import com.knowflow.ai.workspace.CreateWorkspaceRequest;
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
import java.util.UUID;

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

    @Test
    public void testDocumentUploadAndLifecycle() throws Exception {
        String token = registerAndGetToken("Doc Tester", "doctest-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "Engineering Documents");

        // 1. Upload text document
        String fileContent = "KnowFlow AI Enterprise Policy Document.\n"
                + "Employees are granted 25 days of annual paid leave per calendar year.\n"
                + "Technical support is available 24/7 via Slack #help-desk.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "Corporate_Policy.txt", "text/plain", fileContent.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/workspaces/" + workspaceId + "/documents/upload")
                        .file(file)
                        .param("title", "Corporate Policy 2027")
                        .param("category", "Policies")
                        .param("folderPath", "/hr")
                        .param("accessLevel", DocumentAccessLevel.WORKSPACE_SHARED.name())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Corporate Policy 2027"))
                .andExpect(jsonPath("$.data.fileType").value("TXT"))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andReturn();

        JsonNode docJson = objectMapper.readTree(uploadRes.getResponse().getContentAsString());
        String docId = docJson.path("data").path("id").asText();

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
}
