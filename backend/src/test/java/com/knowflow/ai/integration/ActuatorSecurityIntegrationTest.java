package com.knowflow.ai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.auth.RegisterRequest;
import com.knowflow.ai.security.JwtTokenProvider;
import com.knowflow.ai.user.GlobalRole;
import com.knowflow.ai.user.User;
import com.knowflow.ai.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
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
public class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String registerAndGetToken(String name, String email) throws Exception {
        RegisterRequest reg = new RegisterRequest(name, email, "Password123!");
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    @Test
    @DisplayName("Unauthenticated request to /actuator/health returns basic UP status without exposing component details")
    public void testPublicHealthEndpointDoesNotExposeDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // Ensure internal components like diskSpace, db, paths are NOT exposed to unauthenticated users
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("Unauthenticated request to liveness and readiness probes return status UP")
    public void testLivenessAndReadinessProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Unauthenticated request to /actuator/metrics is rejected")
    public void testMetricsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Regular USER cannot access /actuator/metrics (forbidden)")
    public void testRegularUserCannotAccessMetrics() throws Exception {
        String email = "regular-" + UUID.randomUUID() + "@enterprise.com";
        String userToken = registerAndGetToken("Regular User", email);

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPER_ADMIN can access /actuator/metrics")
    public void testSuperAdminCanAccessMetrics() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@enterprise.com";
        String adminToken = registerAndGetToken("Admin User", email);

        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        user.setGlobalRole(GlobalRole.SUPER_ADMIN);
        userRepository.save(user);

        // Re-generate token with SUPER_ADMIN role claim
        String superAdminToken = jwtTokenProvider.generateAccessToken(user.getId(), email, "ROLE_SUPER_ADMIN");

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
}
