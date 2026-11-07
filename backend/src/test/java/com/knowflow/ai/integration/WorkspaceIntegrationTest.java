package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.workspace.CreateWorkspaceRequest;
import com.knowflow.ai.workspace.InviteMemberRequest;
import com.knowflow.ai.workspace.UpdateWorkspaceRequest;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class WorkspaceIntegrationTest {

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

    @Test
    public void testWorkspaceLifecycleAndTenantIsolation() throws Exception {
        String tokenA = registerAndGetToken("User Alpha", "alpha-" + UUID.randomUUID() + "@enterprise.io");
        String tokenB = registerAndGetToken("User Beta", "beta-" + UUID.randomUUID() + "@enterprise.io");

        // 1. User A creates a workspace
        CreateWorkspaceRequest createReq = new CreateWorkspaceRequest("Acme AI Labs", "Autonomous intelligence lab");
        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme AI Labs"))
                .andExpect(jsonPath("$.data.userRole").value("WORKSPACE_ADMIN"))
                .andReturn();

        JsonNode wsJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        String workspaceId = wsJson.path("data").path("id").asText();

        // 2. User A updates workspace
        UpdateWorkspaceRequest updateReq = new UpdateWorkspaceRequest("Acme AI Research", "Updated description");
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme AI Research"));

        // 3. User B (Non-member) attempts to access User A's workspace -> 403 Forbidden!
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // 4. User A invites User B as EDITOR via invitation workflow
        String userBEmail = "beta-invited-" + UUID.randomUUID() + "@enterprise.io";
        String tokenInvited = registerAndGetToken("Invited User", userBEmail);

        InviteMemberRequest inviteReq = new InviteMemberRequest(userBEmail, WorkspaceRole.EDITOR);
        MvcResult inviteRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(userBEmail))
                .andExpect(jsonPath("$.data.role").value("EDITOR"))
                .andReturn();

        JsonNode inviteJson = objectMapper.readTree(inviteRes.getResponse().getContentAsString());
        String inviteToken = inviteJson.path("data").path("token").asText();

        // User B accepts the invitation to become an active member
        mockMvc.perform(post("/api/v1/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + tokenInvited))
                .andExpect(status().isOk());

        // 5. Get members list
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
