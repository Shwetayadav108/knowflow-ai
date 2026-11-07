package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.retrieval.SearchRequest;
import com.knowflow.ai.workspace.CreateWorkspaceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class SearchIntegrationTest {

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
    public void testHybridSearchEndpoint() throws Exception {
        String token = registerAndGetToken("Search Tester", "search-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "Search Workspace");

        SearchRequest req = new SearchRequest();
        req.setQuery("Annual leave policy");
        req.setTopK(5);
        req.setSimilarityThreshold(0.40);

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void testNonMemberCannotSearchWorkspace_returnsHttp403Forbidden() throws Exception {
        String ownerToken = registerAndGetToken("Workspace Owner", "owner-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(ownerToken, "Confidential Workspace");

        String nonMemberToken = registerAndGetToken("Non Member", "nonmember-" + UUID.randomUUID() + "@enterprise.io");

        SearchRequest req = new SearchRequest();
        req.setQuery("Confidential strategy");
        req.setTopK(5);
        req.setSimilarityThreshold(0.40);

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/search")
                        .header("Authorization", "Bearer " + nonMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden"))
                .andExpect(jsonPath("$.error").value("You do not have permission to perform this action."));
    }
}
