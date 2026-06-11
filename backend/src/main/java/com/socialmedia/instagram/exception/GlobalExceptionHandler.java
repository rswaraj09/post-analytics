package com.socialmedia.instagram.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API
 * Implements standardized error responses
 * Requirement 13.6: Return HTTP 403 Forbidden for unauthorized access
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle ForbiddenException - HTTP 403
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(
            ForbiddenException ex,
            WebRequest request) {
        log.warn("Forbidden access attempt: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle Spring Security AccessDeniedException - HTTP 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You do not have permission to perform this action")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle validation errors - HTTP 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid request data")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .validationErrors(errors)
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle generic exceptions - HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        log.error("Unhandled exception", ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Error response DTO
     */
    public static class ErrorResponse {
        private final int status;
        private final String error;
        private final String message;
        private final String path;
        private final Instant timestamp;
        private final Map<String, String> validationErrors;
        
        private ErrorResponse(Builder builder) {
            this.status = builder.status;
            this.error = builder.error;
            this.message = builder.message;
            this.path = builder.path;
            this.timestamp = builder.timestamp;
            this.validationErrors = builder.validationErrors;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getMessage() { return message; }
        public String getPath() { return path; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, String> getValidationErrors() { return validationErrors; }
        
        public static class Builder {
            private int status;
            private String error;
            private String message;
            private String path;
            private Instant timestamp;
            private Map<String, String> validationErrors;
            
            public Builder status(int status) {
                this.status = status;
                return this;
            }
            
            public Builder error(String error) {
                this.error = error;
                return this;
            }
            
            public Builder message(String message) {
                this.message = message;
                return this;
            }
            
            public Builder path(String path) {
                this.path = path;
                return this;
            }
            
            public Builder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public Builder validationErrors(Map<String, String> validationErrors) {
                this.validationErrors = validationErrors;
                return this;
            }
            
            public ErrorResponse build() {
                return new ErrorResponse(this);
            }
        }
    }
}
