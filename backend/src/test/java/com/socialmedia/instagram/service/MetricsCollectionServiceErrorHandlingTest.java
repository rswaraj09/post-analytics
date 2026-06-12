package com.socialmedia.instagram.service;

import com.socialmedia.instagram.entity.InstagramPost;
import com.socialmedia.instagram.entity.InstagramProfile;
import com.socialmedia.instagram.entity.MonitoringStatus;
import com.socialmedia.instagram.repository.InstagramPostRepository;
import com.socialmedia.instagram.repository.InstagramProfileRepository;
import com.socialmedia.instagram.repository.PostMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for error handling in MetricsCollectionService
 * Tests Requirements 5.5, 5.6, 15.2
 * 
 * Task 8.4: Verify error handling for Graph API
 * - Rate limit errors trigger exponential backoff (Requirement 15.2)
 * - Token refresh failures clear the profile's graphApiToken (Requirement 5.5)
 * - Errors are properly logged (Requirement 5.6)
 * - Profile token marked as expired/cleared on permanent failure (Requirement 5.5)
 */
@ExtendWith(MockitoExtension.class)
class MetricsCollectionServiceErrorHandlingTest {

    @Mock
    private InstagramPostRepository postRepository;

    @Mock
    private PostMetricsRepository metricsRepository;

    @Mock
    private InstagramProfileRepository profileRepository;

    @Mock
    private ScrapingService scrapingService;

    @Mock
    private GraphApiService graphApiService;

    private MetricsCollectionService service;

    @BeforeEach
    void setUp() {
        service = new MetricsCollectionService(
            postRepository,
            metricsRepository,
            profileRepository,
            scrapingService,
            graphApiService
        );
    }

    /**
     * Test that token expiration triggers handleTokenExpiration
     * Validates Requirement 5.5: Token expiration handling
     */
    @Test
    void testTokenExpirationTriggersHandling() throws Exception {
        // Arrange
        UUID postId = UUID.randomUUID();
        InstagramProfile profile = createBusinessProfile("testuser", "valid-token");
        InstagramPost post = createPost(postId, profile, "ABC123");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        // Simulate token expiration error when fetchPostInsights is called
        GraphApiException tokenExpiredError = new GraphApiException(
            "Error validating access token", 
            401, 
            "OAuthException"
        );
        
        when(graphApiService.validateToken(anyString())).thenReturn(
            com.socialmedia.instagram.dto.TokenValidation.builder()
                .isValid(true)
                .build()
        );
        
        when(graphApiService.fetchPostInsights(anyString(), anyString()))
            .thenThrow(tokenExpiredError);

        // Mock refresh to also fail (permanent failure)
        when(graphApiService.refreshAccessToken(anyString()))
            .thenThrow(new GraphApiException("Cannot refresh token", 401, "OAuthException"));

        // Act & Assert
        assertThrows(GraphApiException.class, () -> {
            service.collectMetrics(postId);
        });

        // Verify that profile token was cleared (Requirement 5.5)
        ArgumentCaptor<InstagramProfile> profileCaptor = ArgumentCaptor.forClass(InstagramProfile.class);
        verify(profileRepository, atLeastOnce()).save(profileCaptor.capture());
        
        InstagramProfile savedProfile = profileCaptor.getValue();
        assertNull(savedProfile.getGraphApiToken(), 
            "Profile token should be cleared after permanent refresh failure");
    }

    /**
     * Test that token refresh success updates the profile token
     * Validates Requirement 5.4: Token refresh on expiration
     */
    @Test
    void testTokenRefreshSuccessUpdatesProfile() throws Exception {
        // Arrange
        UUID postId = UUID.randomUUID();
        InstagramProfile profile = createBusinessProfile("testuser", "old-token");
        InstagramPost post = createPost(postId, profile, "ABC123");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        // Simulate token expiration error
        GraphApiException tokenExpiredError = new GraphApiException(
            "Token expired", 
            190, 
            "TOKEN_EXPIRED"
        );
        
        when(graphApiService.validateToken("old-token")).thenThrow(tokenExpiredError);
        
        // Mock successful refresh
        String newToken = "refreshed-token";
        when(graphApiService.refreshAccessToken("old-token")).thenReturn(newToken);

        // Mock fetchPostInsights to throw again (to end the test)
        when(graphApiService.fetchPostInsights(anyString(), anyString()))
            .thenThrow(new GraphApiException("Still expired", 401, "OAuthException"));

        // Act & Assert
        assertThrows(GraphApiException.class, () -> {
            service.collectMetrics(postId);
        });

        // Verify that profile was saved with new token
        ArgumentCaptor<InstagramProfile> profileCaptor = ArgumentCaptor.forClass(InstagramProfile.class);
        verify(profileRepository, atLeastOnce()).save(profileCaptor.capture());
        
        // Check if any saved profile has the new token
        boolean tokenUpdated = profileCaptor.getAllValues().stream()
            .anyMatch(p -> newToken.equals(p.getGraphApiToken()));
        
        assertTrue(tokenUpdated, 
            "Profile should be updated with new token after successful refresh");
    }

    /**
     * Test that token refresh failure clears the token
     * Validates Requirement 5.5: Token marked as expired on refresh failure
     */
    @Test
    void testTokenRefreshFailureClearsToken() throws Exception {
        // Arrange
        UUID postId = UUID.randomUUID();
        InstagramProfile profile = createBusinessProfile("testuser", "expired-token");
        InstagramPost post = createPost(postId, profile, "ABC123");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        // Simulate token expiration when fetchPostInsights is called
        GraphApiException tokenExpiredError = new GraphApiException(
            "Token is expired", 
            190, 
            "TOKEN_EXPIRED"
        );
        
        when(graphApiService.validateToken(anyString())).thenReturn(
            com.socialmedia.instagram.dto.TokenValidation.builder()
                .isValid(true)
                .build()
        );
        
        when(graphApiService.fetchPostInsights(anyString(), anyString()))
            .thenThrow(tokenExpiredError);
        
        // Mock refresh failure
        when(graphApiService.refreshAccessToken("expired-token"))
            .thenThrow(new GraphApiException("Refresh failed", 400, "INVALID_REQUEST"));

        // Act & Assert
        assertThrows(GraphApiException.class, () -> {
            service.collectMetrics(postId);
        });

        // Verify token was cleared
        ArgumentCaptor<InstagramProfile> profileCaptor = ArgumentCaptor.forClass(InstagramProfile.class);
        verify(profileRepository, atLeastOnce()).save(profileCaptor.capture());
        
        InstagramProfile savedProfile = profileCaptor.getValue();
        assertNull(savedProfile.getGraphApiToken(), 
            "Token should be cleared (set to null) when refresh fails permanently");
    }

    /**
     * Test that rate limit errors are properly identified
     * Validates Requirement 15.2: Rate limit error detection
     * 
     * Note: The exponential backoff retry is handled by Spring's @Retryable annotation
     * on InstagramGraphApiService.fetchPostInsights(). This test verifies the error
     * is properly classified as retriable.
     */
    @Test
    void testRateLimitErrorIsRetriable() {
        // Arrange - Create rate limit error (HTTP 429)
        GraphApiException rateLimitError = new GraphApiException(
            "Rate limit exceeded", 
            429, 
            "RATE_LIMIT"
        );

        // Assert - Verify it's detected as rate limit error
        assertTrue(rateLimitError.isRateLimitError(), 
            "HTTP 429 should be detected as rate limit error for retry");
        assertFalse(rateLimitError.isTokenExpiredError(), 
            "Rate limit error should not be confused with token expiration");
    }

    /**
     * Test that OAuthRateLimitException errors are properly identified
     * Validates Requirement 15.2: Rate limit error detection
     */
    @Test
    void testOAuthRateLimitExceptionIsRetriable() {
        // Arrange - Create OAuthRateLimitException
        GraphApiException rateLimitError = new GraphApiException(
            "Application request limit reached", 
            403, 
            "OAuthRateLimitException"
        );

        // Assert - Verify it's detected as rate limit error
        assertTrue(rateLimitError.isRateLimitError(), 
            "OAuthRateLimitException should be detected as rate limit error for retry");
    }

    /**
     * Test that post monitoring status is updated to ERROR on collection failure
     * Validates Requirement 5.6: Error logging and state management
     */
    @Test
    void testCollectionFailureUpdatesMonitoringStatus() throws Exception {
        // Arrange
        UUID postId = UUID.randomUUID();
        InstagramProfile profile = createBusinessProfile("testuser", "valid-token");
        InstagramPost post = createPost(postId, profile, "ABC123");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        // Simulate a non-retriable error from fetchPostInsights
        GraphApiException permanentError = new GraphApiException(
            "Media not found", 
            404, 
            "NOT_FOUND"
        );
        
        when(graphApiService.validateToken(anyString())).thenReturn(
            com.socialmedia.instagram.dto.TokenValidation.builder()
                .isValid(true)
                .build()
        );
        
        when(graphApiService.fetchPostInsights(anyString(), anyString()))
            .thenThrow(permanentError);

        // Act & Assert
        assertThrows(GraphApiException.class, () -> {
            service.collectMetrics(postId);
        });

        // Verify post status was updated to ERROR
        ArgumentCaptor<InstagramPost> postCaptor = ArgumentCaptor.forClass(InstagramPost.class);
        verify(postRepository, atLeastOnce()).save(postCaptor.capture());
        
        InstagramPost savedPost = postCaptor.getValue();
        assertEquals(MonitoringStatus.ERROR, savedPost.getMonitoringStatus(), 
            "Post monitoring status should be set to ERROR after collection failure");
    }

    // Helper methods

    private InstagramProfile createBusinessProfile(String username, String token) {
        InstagramProfile profile = new InstagramProfile();
        profile.setId(UUID.randomUUID());
        profile.setUsername(username);
        profile.setProfileUrl("https://www.instagram.com/" + username + "/");
        profile.setIsBusinessAccount(true);
        profile.setGraphApiToken(token);
        return profile;
    }

    private InstagramPost createPost(UUID postId, InstagramProfile profile, String shortcode) {
        InstagramPost post = new InstagramPost();
        post.setId(postId);
        post.setProfile(profile);
        post.setPostUrl("https://www.instagram.com/p/" + shortcode + "/");
        post.setPostShortcode(shortcode);
        post.setMonitoringStatus(MonitoringStatus.ACTIVE);
        return post;
    }
}
