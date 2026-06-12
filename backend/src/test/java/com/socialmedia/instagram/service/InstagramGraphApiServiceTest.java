package com.socialmedia.instagram.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstagramGraphApiService
 * Tests Requirements 5.1, 5.2, 5.4, 5.5, 5.6
 * 
 * Note: These tests focus on input validation and configuration validation.
 * Full integration testing with actual Instagram Graph API calls would require
 * valid access tokens and would be better suited for integration test suites.
 */
class InstagramGraphApiServiceTest {

    private InstagramGraphApiService service;

    @BeforeEach
    void setUp() {
        service = new InstagramGraphApiService();
        
        // Set configuration properties
        ReflectionTestUtils.setField(service, "appId", "test-app-id");
        ReflectionTestUtils.setField(service, "appSecret", "test-app-secret");
    }

    /**
     * Test fetchPostInsights with null media ID
     * Validates Requirement 5.1: Input validation
     */
    @Test
    void testFetchPostInsights_NullMediaId() {
        // Arrange
        String mediaId = null;
        String accessToken = "valid-token";
        
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.fetchPostInsights(mediaId, accessToken);
        });
        
        assertTrue(exception.getMessage().contains("Media ID cannot be null or empty"));
    }

    /**
     * Test fetchPostInsights with empty media ID
     * Validates input validation
     */
    @Test
    void testFetchPostInsights_EmptyMediaId() {
        // Arrange
        String mediaId = "   "; // Whitespace only
        String accessToken = "valid-token";
        
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.fetchPostInsights(mediaId, accessToken);
        });
        
        assertTrue(exception.getMessage().contains("Media ID cannot be null or empty"));
    }

    /**
     * Test fetchPostInsights with empty access token
     * Validates Requirement 5.1: Input validation
     */
    @Test
    void testFetchPostInsights_EmptyAccessToken() {
        // Arrange
        String mediaId = "123456789";
        String accessToken = "";
        
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.fetchPostInsights(mediaId, accessToken);
        });
        
        assertTrue(exception.getMessage().contains("Access token cannot be null or empty"));
    }

    /**
     * Test fetchPostInsights with null access token
     * Validates input validation
     */
    @Test
    void testFetchPostInsights_NullAccessToken() {
        // Arrange
        String mediaId = "123456789";
        String accessToken = null;
        
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.fetchPostInsights(mediaId, accessToken);
        });
        
        assertTrue(exception.getMessage().contains("Access token cannot be null or empty"));
    }

    /**
     * Test validateToken with null token
     * Validates Requirement 5.4: Token validation input validation
     */
    @Test
    void testValidateToken_NullToken() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.validateToken(null);
        });
        
        assertTrue(exception.getMessage().contains("Access token cannot be null or empty"));
    }

    /**
     * Test validateToken with empty token
     * Validates input validation
     */
    @Test
    void testValidateToken_EmptyToken() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.validateToken("  ");
        });
        
        assertTrue(exception.getMessage().contains("Access token cannot be null or empty"));
    }

    /**
     * Test refreshAccessToken with null token
     * Validates Requirement 5.4: Token refresh input validation
     */
    @Test
    void testRefreshAccessToken_NullToken() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.refreshAccessToken(null);
        });
        
        assertTrue(exception.getMessage().contains("Current token cannot be null or empty"));
    }

    /**
     * Test refreshAccessToken with empty token
     * Validates input validation
     */
    @Test
    void testRefreshAccessToken_EmptyToken() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.refreshAccessToken("   ");
        });
        
        assertTrue(exception.getMessage().contains("Current token cannot be null or empty"));
    }

    /**
     * Test refreshAccessToken with missing app credentials
     * Validates Requirement 5.4: Configuration validation
     */
    @Test
    void testRefreshAccessToken_MissingAppId() {
        // Arrange
        ReflectionTestUtils.setField(service, "appId", "");
        
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.refreshAccessToken("valid-token");
        });
        
        assertTrue(exception.getMessage().contains("App ID and App Secret must be configured"));
    }

    /**
     * Test refreshAccessToken with missing app secret
     * Validates configuration validation
     */
    @Test
    void testRefreshAccessToken_MissingAppSecret() {
        // Arrange
        ReflectionTestUtils.setField(service, "appSecret", null);
        
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.refreshAccessToken("valid-token");
        });
        
        assertTrue(exception.getMessage().contains("App ID and App Secret must be configured"));
    }

    /**
     * Test getMediaIdFromShortcode with valid shortcode
     * Validates Requirement 5.1: Media ID extraction
     * Note: Current implementation returns shortcode as-is (simplified)
     */
    @Test
    void testGetMediaIdFromShortcode_Success() throws Exception {
        // Arrange
        String shortcode = "ABC123xyz";
        String accessToken = "valid-token";
        
        // Act
        String result = service.getMediaIdFromShortcode(shortcode, accessToken);
        
        // Assert
        assertNotNull(result);
        assertEquals(shortcode, result); // Current implementation returns shortcode as-is
    }

    /**
     * Test getMediaIdFromShortcode with null shortcode
     * Validates input validation
     */
    @Test
    void testGetMediaIdFromShortcode_NullShortcode() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.getMediaIdFromShortcode(null, "token");
        });
        
        assertTrue(exception.getMessage().contains("Shortcode cannot be null or empty"));
    }

    /**
     * Test getMediaIdFromShortcode with empty shortcode
     * Validates input validation
     */
    @Test
    void testGetMediaIdFromShortcode_EmptyShortcode() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.getMediaIdFromShortcode("  ", "token");
        });
        
        assertTrue(exception.getMessage().contains("Shortcode cannot be null or empty"));
    }

    /**
     * Test getMediaIdFromShortcode with null token
     * Validates input validation
     */
    @Test
    void testGetMediaIdFromShortcode_NullToken() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.getMediaIdFromShortcode("shortcode", null);
        });
        
        assertTrue(exception.getMessage().contains("Access token cannot be null or empty"));
    }

    /**
     * Test getMediaIdFromShortcode with empty token
     * Validates input validation
     */
    @Test
    void testGetMediaIdFromShortcode_EmptyToken() {
        // Act & Assert
        GraphApiException exception = assertThrows(GraphApiException.class, () -> {
            service.getMediaIdFromShortcode("shortcode", "  ");
        });
        
        assertTrue(exception.getMessage().contains("Access token cannot be null or empty"));
    }

    /**
     * Test GraphApiException functionality for rate limit detection
     * Validates Requirement 15.2: Rate limit error detection
     */
    @Test
    void testGraphApiException_RateLimitDetection() {
        // Test status code 429
        GraphApiException exception429 = new GraphApiException("Rate limited", 429, "RATE_LIMIT");
        assertTrue(exception429.isRateLimitError());
        assertEquals(429, exception429.getStatusCode());
        
        // Test OAuthRateLimitException type
        GraphApiException exceptionOAuth = new GraphApiException("Rate limited", 403, "OAuthRateLimitException");
        assertTrue(exceptionOAuth.isRateLimitError());
    }

    /**
     * Test GraphApiException functionality for token expiration detection
     * Validates Requirement 5.5: Token expiration error detection
     */
    @Test
    void testGraphApiException_TokenExpirationDetection() {
        // Test status code 190
        GraphApiException exception190 = new GraphApiException("Token expired", 190, "TOKEN_EXPIRED");
        assertTrue(exception190.isTokenExpiredError());
        assertEquals(190, exception190.getStatusCode());
        
        // Test OAuthException type
        GraphApiException exceptionOAuth = new GraphApiException("Invalid token", 401, "OAuthException");
        assertTrue(exceptionOAuth.isTokenExpiredError());
    }

    /**
     * Test that service is properly annotated as a Spring service
     * Validates Requirement: Service should be a Spring-managed bean
     */
    @Test
    void testServiceAnnotation() {
        // Verify the class has @Service annotation
        assertTrue(service.getClass().isAnnotationPresent(org.springframework.stereotype.Service.class));
    }

    /**
     * Test that configuration properties can be injected
     * Validates proper Spring configuration setup
     */
    @Test
    void testConfigurationProperties() {
        // Verify app ID and secret can be set
        String appId = (String) ReflectionTestUtils.getField(service, "appId");
        String appSecret = (String) ReflectionTestUtils.getField(service, "appSecret");
        
        assertEquals("test-app-id", appId);
        assertEquals("test-app-secret", appSecret);
    }
}

