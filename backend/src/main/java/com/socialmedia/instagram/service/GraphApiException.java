package com.socialmedia.instagram.service;

/**
 * Exception thrown when Instagram Graph API operations fail
 */
public class GraphApiException extends Exception {
    
    private final int statusCode;
    private final String errorType;
    
    public GraphApiException(String message) {
        super(message);
        this.statusCode = 0;
        this.errorType = "UNKNOWN";
    }
    
    public GraphApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorType = "UNKNOWN";
    }
    
    public GraphApiException(String message, int statusCode, String errorType) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    public boolean isRateLimitError() {
        return statusCode == 429 || "OAuthRateLimitException".equals(errorType);
    }
    
    public boolean isTokenExpiredError() {
        return "OAuthException".equals(errorType) || statusCode == 190;
    }
}
