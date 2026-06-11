package com.socialmedia.instagram.exception;

/**
 * Exception thrown when user attempts an action not permitted by their role
 * Results in HTTP 403 Forbidden response
 * Requirement 13.6
 */
public class ForbiddenException extends RuntimeException {
    
    public ForbiddenException(String message) {
        super(message);
    }
    
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
