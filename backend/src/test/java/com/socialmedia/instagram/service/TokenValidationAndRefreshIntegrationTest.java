package com.socialmedia.instagram.service;

import com.socialmedia.instagram.dto.TokenValidation;
import com.socialmedia.instagram.entity.InstagramPost;
import com.socialmedia.instagram.entity.InstagramProfile;
import com.socialmedia.instagram.entity.MonitoringStatus;
import com.socialmedia.instagram.repository.InstagramPostRepository;
import com.socialmedia.instagram.repository.InstagramProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for token validation and refresh functionality
 * Tests Requirements 5.4, 5.5: Token validation, refresh, and expiration handling
 */
@ExtendWith(MockitoExtension.class)
class TokenValidationAndRefreshIntegrationTest {

    @Mock
    private InstagramPostRepository postRepository;

    @Mock
    private InstagramProfileRepository profileRepository;

    @Mock
    private GraphApiService graphApiService;

    @Mock
    private ScrapingService scrapingService;

    @InjectMocks
    private MetricsCollectionService metricsCollectionService;

    private InstagramProfile businessProfile;
    private InstagramPost businessPost;

    @BeforeEach
    void setUp() {
        // Create a business profile with Graph API token
        businessProfile = new InstagramProfile();
        businessProfile.setId(UUID.randomUUID());
        businessProfile.setUsername("business_account");
        businessProfile.setProfileUrl("https://www.instagram.com/business_account/");
        businessProfile.setIsBusinessAccount(true);
        businessProfile.setGraphApiToken("old-expired-token");

        // Create a post belonging to the business profile
        businessPost = new InstagramPost();
        businessPost.setId(UUID.randomUUID());
        businessPost.setProfile(businessProfile);
        businessPost.setPostUrl("https://www.instagram.com/p/ABC123/");
        businessPost.setPostShortcode("ABC123");
        businessPost.setMonitoringStatus(MonitoringStatus.ACTIVE);
    }

    /**
     * Test that valid token is accepted without refresh
     * Validates Requirement 5.4: Token validation before use
     */
    @Test
    void testValidToken_NoRefreshNeeded() throws Exception {
        // Arrange
        TokenValidation validToken = TokenValidation.builder()
            .isValid(true)
            .expiresAt(Instant.now().plusSeconds(3600)) // Expires in 1 hour
            .userId("12345")
            .build();

        when(postRepository.findById(businessPost.getId())).thenReturn(Optional.of(businessPost));
        when(graphApiService.validateToken(anyString())).thenReturn(validToken);
        when(graphApiService.fetchPostInsights(anyString(), anyString())).thenThrow(
            new GraphApiException("Simulated API call")
        );

        // Act
        try {
            metricsCollectionService.collectMetrics(businessPost.getId());
        } catch (GraphApiException e) {
            // Expected - we're just testing token validation flow
        }

        // Assert
        verify(graphApiService, times(1)).validateToken("old-expired-token");
        verify(graphApiService, never()).refreshAccessToken(anyString());
        verify(profileRepository, never()).save(any(InstagramProfile.class));
    }

    /**
     * Test that expired token triggers refresh
     * Validates Requirement 5.4: Token refresh for expired tokens
     */
    @Test
    void testExpiredToken_TriggersRefresh() throws Exception {
        // Arrange
        TokenValidation expiredToken = TokenValidation.builder()
            .isValid(true)
            .expiresAt(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
            .userId("12345")
            .build();

        String newToken = "new-refreshed-token";

        when(postRepository.findById(businessPost.getId())).thenReturn(Optional.of(businessPost));
        when(graphApiService.validateToken("old-expired-token")).thenReturn(expiredToken);
        when(graphApiService.refreshAccessToken("old-expired-token")).thenReturn(newToken);
        when(graphApiService.fetchPostInsights(anyString(), eq(newToken))).thenThrow(
            new GraphApiException("Simulated API call")
        );

        // Act
        try {
            metricsCollectionService.collectMetrics(businessPost.getId());
        } catch (GraphApiException e) {
            // Expected - we're just testing token refresh flow
        }

        // Assert
        verify(graphApiService, times(1)).validateToken("old-expired-token");
        verify(graphApiService, times(1)).refreshAccessToken("old-expired-token");
        verify(profileRepository, times(1)).save(argThat(profile ->
            profile.getGraphApiToken().equals(newToken)
        ));
    }

    /**
     * Test that invalid token triggers refresh
     * Validates Requirement 5.4: Token validation and refresh
     */
    @Test
    void testInvalidToken_TriggersRefresh() throws Exception {
        // Arrange
        TokenValidation invalidToken = TokenValidation.builder()
            .isValid(false)
            .errorMessage("Invalid OAuth token")
            .build();

        String newToken = "new-refreshed-token";

        when(postRepository.findById(businessPost.getId())).thenReturn(Optional.of(businessPost));
        when(graphApiService.validateToken("old-expired-token")).thenReturn(invalidToken);
        when(graphApiService.refreshAccessToken("old-expired-token")).thenReturn(newToken);
        when(graphApiService.fetchPostInsights(anyString(), eq(newToken))).thenThrow(
            new GraphApiException("Simulated API call")
        );

        // Act
        try {
            metricsCollectionService.collectMetrics(businessPost.getId());
        } catch (GraphApiException e) {
            // Expected - we're just testing token refresh flow
        }

        // Assert
        verify(graphApiService, times(1)).validateToken("old-expired-token");
        verify(graphApiService, times(1)).refreshAccessToken("old-expired-token");
        verify(profileRepository, times(1)).save(argThat(profile ->
            profile.getGraphApiToken().equals(newToken)
        ));
    }

    /**
     * Test that token refresh failure clears the token
     * Validates Requirement 5.5: Handle token refresh failure
     */
    @Test
    void testTokenRefreshFailure_ClearsToken() throws Exception {
        // Arrange
        GraphApiException tokenExpiredError = new GraphApiException(
            "Token expired", 190, "OAuthException"
        );

        TokenValidation invalidToken = TokenValidation.builder()
            .isValid(false)
            .errorMessage("Invalid token")
            .build();

        when(postRepository.findById(businessPost.getId())).thenReturn(Optional.of(businessPost));
        when(graphApiService.validateToken("old-expired-token")).thenReturn(invalidToken);
        when(graphApiService.refreshAccessToken("old-expired-token"))
            .thenThrow(new GraphApiException("Failed to refresh token"));
        when(graphApiService.fetchPostInsights(anyString(), anyString()))
            .thenThrow(tokenExpiredError);

        // Act
        assertThrows(GraphApiException.class, () -> {
            metricsCollectionService.collectMetrics(businessPost.getId());
        });

        // Assert - should attempt refresh twice (once during validation, once during error handling)
        // and clear token on failure
        verify(graphApiService, times(2)).refreshAccessToken("old-expired-token");
        verify(profileRepository, times(1)).save(argThat(profile ->
            profile.getGraphApiToken() == null
        ));
        verify(postRepository, times(1)).save(argThat(post ->
            post.getMonitoringStatus() == MonitoringStatus.ERROR
        ));
    }

    /**
     * Test that token expiration during metrics collection triggers handleTokenExpiration
     * Validates Requirement 5.5: Token expiration error detection and handling
     */
    @Test
    void testMetricsCollection_TokenExpirationError_HandlesGracefully() throws Exception {
        // Arrange
        GraphApiException tokenExpiredError = new GraphApiException(
            "Token expired", 190, "OAuthException"
        );

        TokenValidation validToken = TokenValidation.builder()
            .isValid(true)
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        when(postRepository.findById(businessPost.getId())).thenReturn(Optional.of(businessPost));
        when(graphApiService.validateToken("old-expired-token")).thenReturn(validToken);
        when(graphApiService.fetchPostInsights(anyString(), anyString()))
            .thenThrow(tokenExpiredError);
        
        // First call to refresh succeeds
        when(graphApiService.refreshAccessToken("old-expired-token"))
            .thenReturn("new-token");

        // Act
        assertThrows(GraphApiException.class, () -> {
            metricsCollectionService.collectMetrics(businessPost.getId());
        });

        // Assert - should detect token expiration and attempt refresh
        assertTrue(tokenExpiredError.isTokenExpiredError());
        verify(graphApiService, times(1)).refreshAccessToken("old-expired-token");
    }

    /**
     * Test TokenValidation DTO functionality
     * Validates Requirement 5.4: Token validation data structure
     */
    @Test
    void testTokenValidation_DTO_Functionality() {
        // Test expired token
        TokenValidation expired = TokenValidation.builder()
            .isValid(true)
            .expiresAt(Instant.now().minusSeconds(100))
            .build();
        assertTrue(expired.isExpired());
        assertTrue(expired.getSecondsUntilExpiration() < 0);

        // Test valid token
        TokenValidation valid = TokenValidation.builder()
            .isValid(true)
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        assertFalse(valid.isExpired());
        assertTrue(valid.getSecondsUntilExpiration() > 0);

        // Test token without expiration
        TokenValidation noExpiry = TokenValidation.builder()
            .isValid(true)
            .expiresAt(null)
            .build();
        assertFalse(noExpiry.isExpired());
        assertEquals(0, noExpiry.getSecondsUntilExpiration());
    }

    /**
     * Test GraphApiException error type detection
     * Validates Requirement 5.5, 5.6: Error type classification
     */
    @Test
    void testGraphApiException_ErrorTypeDetection() {
        // Test token expiration detection by status code
        GraphApiException expiredByCode = new GraphApiException("Expired", 190, "TOKEN_EXPIRED");
        assertTrue(expiredByCode.isTokenExpiredError());

        // Test token expiration detection by error type
        GraphApiException expiredByType = new GraphApiException("Expired", 401, "OAuthException");
        assertTrue(expiredByType.isTokenExpiredError());

        // Test rate limit detection
        GraphApiException rateLimitByCode = new GraphApiException("Rate limit", 429, "RATE_LIMIT");
        assertTrue(rateLimitByCode.isRateLimitError());

        GraphApiException rateLimitByType = new GraphApiException("Rate limit", 403, "OAuthRateLimitException");
        assertTrue(rateLimitByType.isRateLimitError());

        // Test other errors
        GraphApiException otherError = new GraphApiException("Other error", 500, "INTERNAL_ERROR");
        assertFalse(otherError.isTokenExpiredError());
        assertFalse(otherError.isRateLimitError());
    }

    /**
     * Test that public posts don't attempt token validation
     * Validates correct flow routing based on account type
     */
    @Test
    void testPublicPost_SkipsTokenValidation() throws Exception {
        // Arrange - create public profile (non-business account)
        InstagramProfile publicProfile = new InstagramProfile();
        publicProfile.setId(UUID.randomUUID());
        publicProfile.setUsername("public_account");
        publicProfile.setIsBusinessAccount(false);
        publicProfile.setGraphApiToken(null);

        InstagramPost publicPost = new InstagramPost();
        publicPost.setId(UUID.randomUUID());
        publicPost.setProfile(publicProfile);
        publicPost.setPostUrl("https://www.instagram.com/p/XYZ789/");
        publicPost.setMonitoringStatus(MonitoringStatus.ACTIVE);

        when(postRepository.findById(publicPost.getId())).thenReturn(Optional.of(publicPost));
        when(scrapingService.scrapePost(anyString())).thenThrow(
            new ScrapingException("Simulated scraping")
        );

        // Act
        assertThrows(ScrapingException.class, () -> {
            metricsCollectionService.collectMetrics(publicPost.getId());
        });

        // Assert - should not call Graph API methods
        verify(graphApiService, never()).validateToken(anyString());
        verify(graphApiService, never()).refreshAccessToken(anyString());
        verify(graphApiService, never()).fetchPostInsights(anyString(), anyString());
    }

    /**
     * Test that business account without token falls back to scraping
     * Validates fallback behavior
     */
    @Test
    void testBusinessAccount_NoToken_FallsBackToScraping() throws Exception {
        // Arrange - business account but no token
        businessProfile.setGraphApiToken(null);

        when(postRepository.findById(businessPost.getId())).thenReturn(Optional.of(businessPost));
        when(scrapingService.scrapePost(anyString())).thenThrow(
            new ScrapingException("Simulated scraping")
        );

        // Act
        assertThrows(ScrapingException.class, () -> {
            metricsCollectionService.collectMetrics(businessPost.getId());
        });

        // Assert - should use scraping, not Graph API
        verify(scrapingService, times(1)).scrapePost(businessPost.getPostUrl());
        verify(graphApiService, never()).validateToken(anyString());
        verify(graphApiService, never()).fetchPostInsights(anyString(), anyString());
    }
}
