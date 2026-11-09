package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.chat.SendMessageRequest;
import com.knowflow.ai.chat.SubmitFeedbackRequest;
import com.knowflow.ai.workspace.CreateWorkspaceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class ChatIntegrationTest {

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
    public void testConversationAndChatEndpoints() throws Exception {
        String token = registerAndGetToken("Chat User", "chatuser-" + UUID.randomUUID() + "@enterprise.io");
        String workspaceId = createWorkspace(token, "AI Knowledge Work");

        // 1. Create Conversation
        MvcResult convRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Policy Questions"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Policy Questions"))
                .andReturn();

        JsonNode convJson = objectMapper.readTree(convRes.getResponse().getContentAsString());
        String conversationId = convJson.path("data").path("id").asText();

        // 2. Send Message
        SendMessageRequest msgReq = new SendMessageRequest("What is the company annual leave allowance?");
        MvcResult msgRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sender").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andReturn();

        JsonNode msgJson = objectMapper.readTree(msgRes.getResponse().getContentAsString());
        String messageId = msgJson.path("data").path("id").asText();

        // 3. Submit feedback
        SubmitFeedbackRequest feedbackReq = new SubmitFeedbackRequest(1, "Accurate and prompt response");
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/messages/" + messageId + "/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4. Test SSE Stream endpoint
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/chat/conversations/" + conversationId + "/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isOk());
    }
}
