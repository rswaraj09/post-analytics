package com.socialmedia.instagram.service;

import com.socialmedia.instagram.dto.PostInsights;
import com.socialmedia.instagram.entity.InstagramPost;
import com.socialmedia.instagram.entity.InstagramProfile;
import com.socialmedia.instagram.entity.MonitoringStatus;
import com.socialmedia.instagram.entity.PostMetrics;
import com.socialmedia.instagram.repository.InstagramPostRepository;
import com.socialmedia.instagram.repository.InstagramProfileRepository;
import com.socialmedia.instagram.repository.PostMetricsRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for MetricsCollectionService
 * Tests Requirements 5.3: Graph API metrics are authoritative (not estimated)
 */
class MetricsCollectionServicePropertyTest {

    private InstagramPostRepository postRepository;
    private PostMetricsRepository metricsRepository;
    private InstagramProfileRepository profileRepository;
    private ScrapingService scrapingService;
    private GraphApiService graphApiService;
    private MetricsCollectionService metricsCollectionService;
    
    @BeforeTry
    void setup() {
        // Initialize mocks manually for jqwik compatibility
        postRepository = mock(InstagramPostRepository.class);
        metricsRepository = mock(PostMetricsRepository.class);
        profileRepository = mock(InstagramProfileRepository.class);
        scrapingService = mock(ScrapingService.class);
        graphApiService = mock(GraphApiService.class);
        
        metricsCollectionService = new MetricsCollectionService(
            postRepository,
            metricsRepository,
            profileRepository,
            scrapingService,
            graphApiService
        );
    }

    /**
     * Property 19: Graph API Metrics Not Estimated
     * 
     * **Validates: Requirements 5.3**
     * 
     * For any metrics collected via Instagram Graph API, the isEstimated flag SHALL be false.
     * 
     * This property verifies that when collectViaGraphApi processes any valid PostInsights data
     * from the Graph API, the resulting PostMetrics always has isEstimated=false, confirming
     * that Graph API data is authoritative and not estimated.
     */
    @Property(tries = 100)
    @Label("Property 19: Graph API Metrics Not Estimated")
    void graphApiMetricsNotEstimatedProperty(
            @ForAll("postInsights") PostInsights insights,
            @ForAll("businessPost") InstagramPost post) throws Exception {
        
        // Arrange
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        
        // Mock token validation to return a valid token
        when(graphApiService.validateToken(anyString())).thenReturn(
            com.socialmedia.instagram.dto.TokenValidation.builder()
                .isValid(true)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
        );
        
        when(graphApiService.fetchPostInsights(anyString(), anyString())).thenReturn(insights);
        
        // Mock the metricsRepository.save to capture the PostMetrics being saved
        when(metricsRepository.save(any(PostMetrics.class))).thenAnswer(invocation -> {
            PostMetrics capturedMetrics = invocation.getArgument(0);
            capturedMetrics.setId(UUID.randomUUID()); // Set ID as it would be set by DB
            return capturedMetrics;
        });
        
        // Mock findLatestByPostIdSince to return empty (no duplicate)
        when(metricsRepository.findLatestByPostIdSince(any(UUID.class), any(Instant.class)))
            .thenReturn(Optional.empty());

        // Act
        PostMetrics result = metricsCollectionService.collectMetrics(post.getId());

        // Assert
        // Property 19: Graph API metrics SHALL have isEstimated = false
        assertThat(result.getIsEstimated())
            .as("Graph API metrics must not be estimated (isEstimated should be false)")
            .isFalse();
        
        // Verify the metrics match the insights data
        assertThat(result.getLikesCount()).isEqualTo(insights.getLikesCount());
        assertThat(result.getCommentsCount()).isEqualTo(insights.getCommentsCount());
        assertThat(result.getVideoViews()).isEqualTo(insights.getVideoViews());
        assertThat(result.getReach()).isEqualTo(insights.getReach());
        assertThat(result.getImpressions()).isEqualTo(insights.getImpressions());
        assertThat(result.getSaves()).isEqualTo(insights.getSaved());
        assertThat(result.getShares()).isEqualTo(insights.getShares());
        assertThat(result.getProfileVisits()).isEqualTo(insights.getProfileVisits());
        
        // Verify Graph API was used (not scraping)
        verify(graphApiService, times(1)).fetchPostInsights(anyString(), anyString());
        verify(scrapingService, never()).scrapePost(anyString());
    }

    /**
     * Arbitrary generator for PostInsights
     * Generates random valid Graph API insights data
     */
    @Provide
    Arbitrary<PostInsights> postInsights() {
        // Combine first 8 parameters
        Arbitrary<PostInsights.PostInsightsBuilder> builderArbitrary = Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20), // mediaId
            Arbitraries.longs().between(0L, 1_000_000L), // likesCount
            Arbitraries.longs().between(0L, 100_000L),   // commentsCount
            Arbitraries.longs().between(0L, 10_000_000L), // videoViews
            Arbitraries.longs().between(0L, 5_000_000L),  // reach
            Arbitraries.longs().between(0L, 10_000_000L), // impressions
            Arbitraries.longs().between(0L, 500_000L),    // saved
            Arbitraries.longs().between(0L, 100_000L)     // shares
        ).as((mediaId, likes, comments, videoViews, reach, impressions, saved, shares) ->
            PostInsights.builder()
                .mediaId(mediaId)
                .likesCount(likes)
                .commentsCount(comments)
                .videoViews(videoViews)
                .reach(reach)
                .impressions(impressions)
                .saved(saved)
                .shares(shares)
        );
        
        // Add the 9th parameter (profileVisits)
        return Combinators.combine(
            builderArbitrary,
            Arbitraries.longs().between(0L, 50_000L) // profileVisits
        ).as((builder, profileVisits) ->
            builder.profileVisits(profileVisits).build()
        );
    }

    /**
     * Arbitrary generator for business account posts with valid Graph API tokens
     */
    @Provide
    Arbitrary<InstagramPost> businessPost() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30), // username
            Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(20), // shortcode
            Arbitraries.strings().alpha().numeric().ofMinLength(20).ofMaxLength(100) // token
        ).as((username, shortcode, token) -> {
            // Create business profile with Graph API token
            InstagramProfile profile = new InstagramProfile();
            profile.setId(UUID.randomUUID());
            profile.setUsername(username);
            profile.setProfileUrl("https://www.instagram.com/" + username + "/");
            profile.setIsBusinessAccount(true);
            profile.setGraphApiToken(token);
            
            // Create post for business account
            InstagramPost post = new InstagramPost();
            post.setId(UUID.randomUUID());
            post.setProfile(profile);
            post.setPostUrl("https://www.instagram.com/p/" + shortcode + "/");
            post.setPostShortcode(shortcode);
            post.setMonitoringStatus(MonitoringStatus.ACTIVE);
            
            return post;
        });
    }
}
