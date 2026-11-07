package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.user.GlobalRole;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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
    public void testAdminSecurityAnd403ForNonAdmins() throws Exception {
        // Register a regular user
        String standardUserEmail = "regular.dev-" + UUID.randomUUID() + "@enterprise.io";
        String regularToken = registerAndGetToken("Regular Dev", standardUserEmail);

        // Explicitly ensure user is GlobalRole.USER
        User user = userRepository.findByEmailAndDeletedAtIsNull(standardUserEmail).orElseThrow();
        user.setGlobalRole(GlobalRole.USER);
        userRepository.save(user);

        // 1. Regular user accessing /api/v1/admin/stats MUST be rejected with 403 Forbidden!
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(status().isForbidden());

        // 2. Grant user SUPER_ADMIN role
        user.setGlobalRole(GlobalRole.SUPER_ADMIN);
        userRepository.save(user);

        // Re-login to get updated JWT claims with ROLE_SUPER_ADMIN
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.knowflow.ai.auth.LoginRequest(standardUserEmail, "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        String superAdminToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // 3. SuperAdmin accessing /api/v1/admin/stats succeeds with 200 OK!
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.systemHealth").value("OPERATIONAL"));
    }
}
