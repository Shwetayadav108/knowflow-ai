package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.workspace.CreateWorkspaceRequest;
import com.knowflow.ai.workspace.InviteMemberRequest;
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
public class InvitationIntegrationTest {

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
    public void testFullInvitationLifecycle() throws Exception {
        String tokenA = registerAndGetToken("Admin User", "admin-" + UUID.randomUUID() + "@enterprise.io");

        // 1. Admin creates workspace
        CreateWorkspaceRequest createReq = new CreateWorkspaceRequest("Invite Test Workspace", "Test Description");
        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode wsJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        String workspaceId = wsJson.path("data").path("id").asText();

        // 2. Admin invites an external email with EDITOR role
        String inviteEmail = "collaborator-" + UUID.randomUUID() + "@enterprise.io";
        InviteMemberRequest inviteReq = new InviteMemberRequest(inviteEmail, WorkspaceRole.EDITOR);
        MvcResult inviteRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(inviteEmail))
                .andExpect(jsonPath("$.data.role").value("EDITOR"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        JsonNode inviteJson = objectMapper.readTree(inviteRes.getResponse().getContentAsString());
        String inviteToken = inviteJson.path("data").path("token").asText();

        // 3. Public token inspection endpoint
        mockMvc.perform(get("/api/v1/invitations/" + inviteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(inviteEmail))
                .andExpect(jsonPath("$.data.workspaceName").value("Invite Test Workspace"))
                .andExpect(jsonPath("$.data.role").value("EDITOR"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 4. Invitee registers using the invite token
        RegisterRequest registerWithInvite = new RegisterRequest("Collaborator", inviteEmail, "SecurePass123!");
        registerWithInvite.setInviteToken(inviteToken);

        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerWithInvite)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode regJson = objectMapper.readTree(regRes.getResponse().getContentAsString());
        String inviteeToken = regJson.path("data").path("token").asText();

        // 5. Invitee can now access the workspace with EDITOR role
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userRole").value("EDITOR"));

        // 6. Admin can see the invitation is now ACCEPTED
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACCEPTED"));

        // 7. Revoke flow: create another invitation and revoke it
        InviteMemberRequest invite2 = new InviteMemberRequest("revoke-" + UUID.randomUUID() + "@enterprise.io", WorkspaceRole.VIEWER);
        MvcResult invite2Res = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invite2)))
                .andExpect(status().isOk())
                .andReturn();
        String invite2Id = objectMapper.readTree(invite2Res.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/invitations/" + invite2Id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }
}
