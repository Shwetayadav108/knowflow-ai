package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.chat.DocumentComparisonRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class DocumentComparisonTest {

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

    private UUID uploadDoc(String token, String workspaceId, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8)
        );
        MvcResult res = mockMvc.perform(multipart("/api/v1/workspaces/" + workspaceId + "/documents/upload")
                        .file(file)
                        .param("title", filename)
                        .param("accessLevel", DocumentAccessLevel.WORKSPACE_SHARED.name())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return UUID.fromString(json.path("data").path("id").asText());
    }

    @Test
    public void testCompareTwoDocumentsEndpoint() throws Exception {
        String token = registerAndGetToken("Comparison Specialist", "compare-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "Policy Comparison Lab");

        UUID docId1 = uploadDoc(token, workspaceId, "Version_2026.txt", "The remote work policy allows 2 days work from home per week.");
        UUID docId2 = uploadDoc(token, workspaceId, "Version_2027.txt", "The remote work policy allows 4 days work from home per week.");

        DocumentComparisonRequest compareReq = new DocumentComparisonRequest();
        compareReq.setDocId1(docId1);
        compareReq.setDocId2(docId2);
        compareReq.setInstruction("Compare the remote work flexibility between 2026 and 2027 policies.");

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/compare")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compareReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isString())
                .andExpect(jsonPath("$.data.sender").value("ASSISTANT"));
    }
}
