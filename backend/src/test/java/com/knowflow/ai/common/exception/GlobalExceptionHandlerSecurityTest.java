package com.knowflow.ai.common.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class GlobalExceptionHandlerSecurityTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ListAppender<ILoggingEvent> listAppender;
    private Logger handlerLogger;

    @RestController
    static class TestSecurityExceptionController {

        @GetMapping("/test/resource-not-found")
        public void throwResourceNotFound() {
            throw new ResourceNotFoundException("Document file missing at /var/data/storage/workspace_99/secret_contract.pdf");
        }

        @GetMapping("/test/unauthorized")
        public void throwUnauthorized() {
            throw new UnauthorizedException("Token expired or malformed: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sensitive-jwt-token-content");
        }

        @GetMapping("/test/custom-access-denied")
        public void throwCustomAccessDenied() {
            throw new AccessDeniedException("User 104 is not authorized to execute SELECT * FROM payroll WHERE department='executive'");
        }

        @GetMapping("/test/spring-security-access-denied")
        public void throwSpringSecurityAccessDenied() {
            throw new org.springframework.security.access.AccessDeniedException("Internal permission check failed at com.knowflow.ai.security.RoleHierarchy");
        }

        @GetMapping("/test/validation-exception")
        public void throwValidationException() {
            throw new ValidationException("Failed validating path C:\\Windows\\System32\\drivers\\etc\\hosts for user hash 7b8a1c9e");
        }

        @GetMapping("/test/data-integrity")
        public void throwDataIntegrityViolation() {
            throw new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint 'uq_users_ssn' Key (ssn)=(000-11-2222) already exists in table users_pii");
        }

        @GetMapping("/test/data-access")
        public void throwDataAccessException() {
            throw new DataAccessException("Connection failed to postgresql://postgres:SuperSecretPassword123!@10.0.0.99:5432/knowflow_db") {};
        }

        @GetMapping("/test/unexpected-exception")
        public void throwUnexpectedException() {
            throw new RuntimeException("NullPointerException in VaultService: AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY is null");
        }

        @PostMapping("/test/model-validation")
        public String validatePayload(@Valid @RequestBody TestRequest request) {
            return "ok";
        }
    }

    static class TestRequest {
        @NotBlank(message = "Title must not be blank")
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new TestSecurityExceptionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        handlerLogger.addAppender(listAppender);
    }

    @AfterEach
    public void teardown() {
        if (handlerLogger != null && listAppender != null) {
            handlerLogger.detachAppender(listAppender);
        }
    }

    private String getCapturedLogs() {
        return listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("ResourceNotFoundException returns 404 and does not leak raw paths into response or logs")
    public void testResourceNotFoundSanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.error").value("Resource not found."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("/var/data/storage");
        assertThat(body).doesNotContain("secret_contract.pdf");
        assertThat(body).doesNotContain("workspace_99");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Resource not found: type=ResourceNotFoundException, status=404");
        assertThat(logs).doesNotContain("/var/data/storage");
        assertThat(logs).doesNotContain("secret_contract.pdf");
        assertThat(logs).doesNotContain("workspace_99");
    }

    @Test
    @DisplayName("UnauthorizedException returns 401 and does not expose tokens in response or logs")
    public void testUnauthorizedSanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.error").value("Authentication required."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(body).doesNotContain("sensitive-jwt-token");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Unauthorized access attempt: type=UnauthorizedException, status=401");
        assertThat(logs).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(logs).doesNotContain("sensitive-jwt-token");
    }

    @Test
    @DisplayName("Custom AccessDeniedException returns 403 and does not expose SQL in response or logs")
    public void testCustomAccessDeniedSanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/custom-access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden"))
                .andExpect(jsonPath("$.error").value("You do not have permission to perform this action."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SELECT * FROM payroll");
        assertThat(body).doesNotContain("executive");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Access denied: type=AccessDeniedException, status=403");
        assertThat(logs).doesNotContain("SELECT * FROM payroll");
        assertThat(logs).doesNotContain("executive");
    }

    @Test
    @DisplayName("Spring Security AccessDeniedException returns 403 and does not expose class names in response or logs")
    public void testSpringSecurityAccessDeniedSanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/spring-security-access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden"))
                .andExpect(jsonPath("$.error").value("You do not have permission to perform this action."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("com.knowflow.ai");
        assertThat(body).doesNotContain("RoleHierarchy");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Spring Security access denied: type=AccessDeniedException, status=403");
        assertThat(logs).doesNotContain("RoleHierarchy");
    }

    @Test
    @DisplayName("ValidationException returns 400 and does not leak filesystem paths in response or logs")
    public void testValidationExceptionSanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/validation-exception"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.error").value("Request validation failed."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("C:\\Windows\\System32");
        assertThat(body).doesNotContain("hosts");
        assertThat(body).doesNotContain("7b8a1c9e");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Business validation error: type=ValidationException, status=400");
        assertThat(logs).doesNotContain("C:\\Windows\\System32");
        assertThat(logs).doesNotContain("hosts");
        assertThat(logs).doesNotContain("7b8a1c9e");
    }

    @Test
    @DisplayName("DataIntegrityViolationException returns 409 and does not leak database table or SSN into response or logs")
    public void testDataIntegritySanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Data conflict"))
                .andExpect(jsonPath("$.error").value("A data integrity or constraint conflict occurred."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("users_pii");
        assertThat(body).doesNotContain("uq_users_ssn");
        assertThat(body).doesNotContain("000-11-2222");

        // Verify captured logs do NOT contain the SSN or table name, and do NOT attach stack trace
        String logs = getCapturedLogs();
        assertThat(logs).contains("Database integrity violation: type=DataIntegrityViolationException, category=DATA_CONFLICT, status=409");
        assertThat(logs).doesNotContain("000-11-2222");
        assertThat(logs).doesNotContain("users_pii");
        assertThat(logs).doesNotContain("uq_users_ssn");

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    @Test
    @DisplayName("DataAccessException returns 500 and does not leak database connection strings or passwords into response or logs")
    public void testDataAccessExceptionSanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/data-access"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Database error"))
                .andExpect(jsonPath("$.error").value("A data persistence error occurred. Please try again later."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SuperSecretPassword123!");
        assertThat(body).doesNotContain("10.0.0.99");
        assertThat(body).doesNotContain("postgresql://");

        // Verify captured logs do NOT contain the database password or connection string
        String logs = getCapturedLogs();
        assertThat(logs).contains("Database access error: type=DataAccessException, category=DATABASE_ERROR, status=500");
        assertThat(logs).doesNotContain("SuperSecretPassword123!");
        assertThat(logs).doesNotContain("10.0.0.99");
        assertThat(logs).doesNotContain("postgresql://");

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    @Test
    @DisplayName("Unexpected general exception returns 500 and does not leak stack traces or cloud credentials into response or logs")
    public void testGeneralExceptionSanitization() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/unexpected-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.error").value("An unexpected error occurred. Please contact support."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("AWS_SECRET_ACCESS_KEY");
        assertThat(body).doesNotContain("wJalrXUtnFEMI");
        assertThat(body).doesNotContain("VaultService");
        assertThat(body).doesNotContain("NullPointerException");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Unhandled server exception: type=RuntimeException, category=INTERNAL_SERVER_ERROR, status=500");
        assertThat(logs).doesNotContain("AWS_SECRET_ACCESS_KEY");
        assertThat(logs).doesNotContain("wJalrXUtnFEMI");
        assertThat(logs).doesNotContain("VaultService");

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    @Test
    @DisplayName("MethodArgumentNotValidException preserves safe field errors while sanitizing unsafe binding errors")
    public void testMethodArgumentNotValidSanitization() throws Exception {
        // Valid constraint violation on title
        TestRequest emptyReq = new TestRequest();
        emptyReq.setTitle("");

        MvcResult result = mockMvc.perform(post("/test/model-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.title").value("Title must not be blank"))
                .andExpect(jsonPath("$.error").value("Invalid request payload."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Title must not be blank");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Method argument validation failed: type=MethodArgumentNotValidException, errorCount=1, status=400");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException sanitizes type conversion and internal class names")
    public void testMethodArgumentNotValidSanitizesTypeMismatch() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // Create a simulated MethodArgumentNotValidException with internal type mismatch details
        BindingResult bindingResult = new org.springframework.validation.BeanPropertyBindingResult(new Object(), "targetObject");
        bindingResult.addError(new FieldError(
                "targetObject",
                "id",
                "invalid-uuid",
                false,
                null,
                null,
                "Failed to convert property value of type 'java.lang.String' to required type 'java.util.UUID' for property 'id'; nested exception is java.lang.IllegalArgumentException: Invalid UUID string: invalid-uuid"
        ));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        var responseEntity = handler.handleMethodArgumentNotValid(ex);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(400);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().isSuccess()).isFalse();

        @SuppressWarnings("unchecked")
        java.util.Map<String, String> fieldErrors = (java.util.Map<String, String>) responseEntity.getBody().getData();
        assertThat(fieldErrors).containsKey("id");
        // Must NOT leak internal classes or stack trace details
        assertThat(fieldErrors.get("id")).isEqualTo("Invalid format or type for this field.");
        assertThat(fieldErrors.get("id")).doesNotContain("java.lang.String");
        assertThat(fieldErrors.get("id")).doesNotContain("java.util.UUID");
        assertThat(fieldErrors.get("id")).doesNotContain("nested exception");

        // Verify captured logs
        String logs = getCapturedLogs();
        assertThat(logs).contains("Method argument validation failed: type=MethodArgumentNotValidException, errorCount=1, status=400");
        assertThat(logs).doesNotContain("java.lang.String");
        assertThat(logs).doesNotContain("java.util.UUID");
        assertThat(logs).doesNotContain("invalid-uuid");
    }
}
