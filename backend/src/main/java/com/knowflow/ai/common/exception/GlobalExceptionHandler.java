package com.knowflow.ai.common.exception;

import com.knowflow.ai.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String getSafeExceptionType(Throwable ex) {
        if (ex == null) {
            return "UnknownException";
        }
        String name = ex.getClass().getSimpleName();
        if (name.isEmpty() && ex.getClass().getSuperclass() != null) {
            name = ex.getClass().getSuperclass().getSimpleName();
        }
        return name.isEmpty() ? "UnknownException" : name;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: type={}, status=404", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Resource not found", "Resource not found."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Endpoint not found: type={}, status=404", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Endpoint not found", "Endpoint not found."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized access attempt: type={}, status=401", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Unauthorized", "Authentication required."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt: type={}, status=401", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed", "Invalid email or password."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: type={}, status=403", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Forbidden", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleSpringSecurityAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Spring Security access denied: type={}, status=403", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Forbidden", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(ValidationException ex) {
        log.warn("Business validation error: type={}, status=400", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation error", "Request validation failed."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField() != null ? fieldError.getField() : "field";
            errors.put(field, sanitizeValidationMessage(fieldError));
        }
        log.warn("Method argument validation failed: type={}, errorCount={}, status=400",
                getSafeExceptionType(ex), errors.size());
        ApiResponse<Object> response = new ApiResponse<>(false, "Validation failed", errors, "Invalid request payload.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private String sanitizeValidationMessage(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        if (defaultMessage == null || defaultMessage.isBlank()) {
            return "Invalid value provided.";
        }

        String lower = defaultMessage.toLowerCase();
        // Check if Spring/Jackson generated a type mismatch or internal binding error message
        if (lower.contains("failed to convert") ||
            lower.contains("typemismatch") ||
            lower.contains("java.") ||
            lower.contains("com.knowflow") ||
            lower.contains("exception") ||
            lower.contains("nested exception")) {
            return "Invalid format or type for this field.";
        }

        // Sanitize any potential filesystem paths or SQL strings
        if (defaultMessage.contains("..") || defaultMessage.contains("/") || defaultMessage.contains("\\") ||
            lower.contains("select ") || lower.contains("insert ") || lower.contains("drop ") || lower.contains("delete ")) {
            return "Invalid field value format.";
        }

        return defaultMessage.trim();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParams(MissingServletRequestParameterException ex) {
        log.warn("Missing required request parameter: type={}, status=400", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Missing parameter", "Required request parameter is missing."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: type={}, status=400", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid parameter", "Invalid format or type for parameter."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP method not supported: type={}, status=405", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("Method not allowed", "The requested HTTP method is not supported for this endpoint."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("HTTP media type not supported: type={}, status=415", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Unsupported media type", "The request media type is not supported."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        log.warn("Upload file size exceeded limit: type={}, status=413", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("Payload too large", "Uploaded file exceeds the maximum permitted limit of 50MB."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Database integrity violation: type={}, category=DATA_CONFLICT, status=409", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Data conflict", "A data integrity or constraint conflict occurred."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataAccessException(DataAccessException ex) {
        log.error("Database access error: type={}, category=DATABASE_ERROR, status=500", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Database error", "A data persistence error occurred. Please try again later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled server exception: type={}, category=INTERNAL_SERVER_ERROR, status=500", getSafeExceptionType(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", "An unexpected error occurred. Please contact support."));
    }
}
