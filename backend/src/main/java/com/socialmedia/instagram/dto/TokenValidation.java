package com.socialmedia.instagram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for Instagram Graph API token validation response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidation {
    
    private boolean isValid;
    private String appId;
    private String userId;
    private Instant expiresAt;
    private Instant issuedAt;
    private String[] scopes;
    private String errorMessage;
    
    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Get time until expiration in seconds
     */
    public long getSecondsUntilExpiration() {
        if (expiresAt == null) {
            return 0;
        }
        return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }
}
