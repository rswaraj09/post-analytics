package com.socialmedia.instagram.service;

import com.socialmedia.instagram.dto.PostInsights;
import com.socialmedia.instagram.dto.ScrapedPostData;
import com.socialmedia.instagram.dto.TokenValidation;
import com.socialmedia.instagram.entity.InstagramPost;
import com.socialmedia.instagram.entity.InstagramProfile;
import com.socialmedia.instagram.entity.MonitoringStatus;
import com.socialmedia.instagram.entity.PostMetrics;
import com.socialmedia.instagram.repository.InstagramPostRepository;
import com.socialmedia.instagram.repository.InstagramProfileRepository;
import com.socialmedia.instagram.repository.PostMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for collecting and storing post metrics
 * Implements Requirements 4.6, 5.1, 5.2, 5.3, 5.4, 5.5
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MetricsCollectionService {

    private final InstagramPostRepository postRepository;
    private final PostMetricsRepository metricsRepository;
    private final InstagramProfileRepository profileRepository;
    private final ScrapingService scrapingService;
    private final GraphApiService graphApiService;

    /**
     * Collect metrics for a single post
     * Implements Requirements 4.6, 5.1, 5.2, 5.3
     * 
     * @param postId Post UUID
     * @return Collected metrics
     * @throws Exception if collection fails
     */
    public PostMetrics collectMetrics(UUID postId) throws Exception {
        log.info("Collecting metrics for post: {}", postId);

        // Fetch post entity
        InstagramPost post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        // Check if monitoring is active
        if (post.getMonitoringStatus() != MonitoringStatus.ACTIVE) {
            throw new IllegalStateException("Post monitoring is not active: " + postId);
        }

        try {
            PostMetrics metrics;
            
            // Determine collection method based on account type and token availability
            InstagramProfile profile = post.getProfile();
            boolean useGraphApi = profile != null && 
                                   profile.getIsBusinessAccount() != null && 
                                   profile.getIsBusinessAccount() &&
                                   profile.getGraphApiToken() != null && 
                                   !profile.getGraphApiToken().trim().isEmpty();
            
            if (useGraphApi) {
                // Use Graph API for business accounts with valid tokens (Requirement 5.1, 5.2)
                log.info("Using Graph API for post {} (business account)", postId);
                metrics = collectViaGraphApi(post);
            } else {
                // Fall back to scraping for public posts (Requirement 4.6)
                log.info("Using web scraping for post {} (public account or no token)", postId);
                metrics = collectViaScraping(post);
            }

            // Check for duplicate snapshot
            if (isDuplicateSnapshot(postId, metrics)) {
                log.info("Skipping duplicate snapshot for post: {}", postId);
                return metrics;
            }

            // Save metrics
            metrics = metricsRepository.save(metrics);

            // Update post's last fetched timestamp
            post.setLastFetchedAt(Instant.now());
            postRepository.save(post);

            log.info("Successfully collected metrics for post: {}. Likes: {}, Comments: {}, Views: {}, Reach: {}, Impressions: {}, Estimated: {}",
                postId, metrics.getLikesCount(), metrics.getCommentsCount(), metrics.getViewsCount(), 
                metrics.getReach(), metrics.getImpressions(), metrics.getIsEstimated());

            return metrics;

        } catch (GraphApiException e) {
            log.error("Failed to collect metrics via Graph API for post {}: {}", postId, e.getMessage());
            
            // If token is expired, try to refresh it (Requirement 5.4, 5.5)
            if (e.isTokenExpiredError()) {
                handleTokenExpiration(post.getProfile());
            }
            
            handleCollectionFailure(post, e);
            throw e;
            
        } catch (ScrapingException e) {
            log.error("Failed to scrape post {}: {}", postId, e.getMessage());
            handleCollectionFailure(post, e);
            throw e;
        }
    }
    
    /**
     * Collect metrics via Instagram Graph API
     * Implements Requirement 5.1, 5.2, 5.3
     * 
     * @param post Post entity
     * @return PostMetrics with Graph API data (isEstimated = false)
     * @throws GraphApiException if API call fails
     */
    private PostMetrics collectViaGraphApi(InstagramPost post) throws GraphApiException {
        String mediaId = extractMediaIdFromShortcode(post.getPostShortcode());
        String token = post.getProfile().getGraphApiToken();
        
        // Validate token before using it (Requirement 5.4)
        try {
            TokenValidation validation = graphApiService.validateToken(token);
            if (!validation.isValid() || validation.isExpired()) {
                log.warn("Token is invalid or expired for profile {}, attempting refresh", 
                    post.getProfile().getUsername());
                token = graphApiService.refreshAccessToken(token);
                
                // Update profile with new token
                post.getProfile().setGraphApiToken(token);
                profileRepository.save(post.getProfile());
            }
        } catch (GraphApiException e) {
            log.error("Failed to validate/refresh token: {}", e.getMessage());
            // Continue with existing token, will fail if truly invalid
        }
        
        // Fetch insights from Graph API
        PostInsights insights = graphApiService.fetchPostInsights(mediaId, token);
        
        // Calculate engagement rate
        long likesCount = insights.getLikesCount() != null ? insights.getLikesCount() : 0L;
        long commentsCount = insights.getCommentsCount() != null ? insights.getCommentsCount() : 0L;
        long reach = insights.getReach() != null ? insights.getReach() : 0L;
        
        BigDecimal engagementRate = calculateEngagementRate(likesCount, commentsCount, reach);
        
        // Create metrics with Graph API data (isEstimated = false, Requirement 5.3)
        return PostMetrics.builder()
            .post(post)
            .likesCount(likesCount)
            .commentsCount(commentsCount)
            .viewsCount(insights.getVideoViews())
            .videoViews(insights.getVideoViews())
            .reach(insights.getReach())
            .impressions(insights.getImpressions())
            .saves(insights.getSaved())
            .shares(insights.getShares())
            .profileVisits(insights.getProfileVisits())
            .engagementRate(engagementRate)
            .isEstimated(false)  // Graph API data is authoritative
            .fetchedAt(Instant.now())
            .build();
    }
    
    /**
     * Collect metrics via web scraping
     * Implements Requirement 4.6
     * 
     * @param post Post entity
     * @return PostMetrics with scraped data (isEstimated = true)
     * @throws ScrapingException if scraping fails
     */
    private PostMetrics collectViaScraping(InstagramPost post) throws ScrapingException {
        // Scrape post metrics
        ScrapedPostData scrapedData = scrapingService.scrapePost(post.getPostUrl());

        // Calculate estimated reach and impressions
        long likesCount = scrapedData.getLikesCount() != null ? scrapedData.getLikesCount() : 0L;
        long commentsCount = scrapedData.getCommentsCount() != null ? scrapedData.getCommentsCount() : 0L;
        long viewsCount = scrapedData.getViewsCount() != null ? scrapedData.getViewsCount() : 0L;

        // Estimated reach = likesCount × random(5, 8)
        long estimatedReach = calculateEstimatedReach(likesCount);

        // Estimated impressions = reach × 1.2
        long estimatedImpressions = calculateEstimatedImpressions(estimatedReach);

        // Calculate engagement rate
        BigDecimal engagementRate = calculateEngagementRate(likesCount, commentsCount, estimatedReach);

        // Create metrics entity with estimated data
        return PostMetrics.builder()
            .post(post)
            .likesCount(likesCount)
            .commentsCount(commentsCount)
            .viewsCount(viewsCount)
            .reach(estimatedReach)
            .impressions(estimatedImpressions)
            .engagementRate(engagementRate)
            .isEstimated(true)  // Scraped data includes estimates
            .fetchedAt(Instant.now())
            .build();
    }
    
    /**
     * Extract media ID from Instagram shortcode
     * For Graph API, we need the numeric media ID
     * This is a simplified version - in production, you'd need to call
     * the Graph API to get the media ID from the shortcode
     */
    private String extractMediaIdFromShortcode(String shortcode) {
        // For now, return the shortcode as-is
        // In production, you would call: GET /{ig-user-id}/media?fields=id&limit=100
        // and find the media with matching shortcode
        return shortcode;
    }
    
    /**
     * Handle token expiration by marking profile token as expired
     * Implements Requirement 5.5
     */
    private void handleTokenExpiration(InstagramProfile profile) {
        if (profile == null) {
            return;
        }
        
        log.error("Token expired for profile: {}", profile.getUsername());
        
        // Try to refresh the token (Requirement 5.4)
        try {
            String currentToken = profile.getGraphApiToken();
            String newToken = graphApiService.refreshAccessToken(currentToken);
            
            profile.setGraphApiToken(newToken);
            profileRepository.save(profile);
            
            log.info("Successfully refreshed token for profile: {}", profile.getUsername());
            
        } catch (GraphApiException e) {
            log.error("Failed to refresh token for profile {}: {}", 
                profile.getUsername(), e.getMessage());
            
            // Mark token as expired by clearing it (Requirement 5.5)
            profile.setGraphApiToken(null);
            profileRepository.save(profile);
            
            // TODO: Create notification for profile owner (Requirement 5.5)
            log.warn("Token refresh failed for profile {}. Profile owner should be notified.", 
                profile.getUsername());
        }
    }

    /**
     * Calculate estimated reach: likesCount × random(5, 8)
     * Implements Requirement 4.4
     */
    public long calculateEstimatedReach(long likesCount) {
        if (likesCount == 0) {
            return 0L;
        }
        // Random value between 5 and 8 (inclusive)
        double multiplier = ThreadLocalRandom.current().nextDouble(5.0, 8.0);
        return (long) (likesCount * multiplier);
    }

    /**
     * Calculate estimated impressions: reach × 1.2
     * Implements Requirement 4.5
     */
    public long calculateEstimatedImpressions(long reach) {
        return (long) (reach * 1.2);
    }

    /**
     * Calculate engagement rate: (likes + comments) / reach × 100
     * Implements Requirement 7.1, 7.2, 7.3
     */
    public BigDecimal calculateEngagementRate(long likesCount, long commentsCount, long reach) {
        long engagements = likesCount + commentsCount;
        
        // If reach is zero or null, use likesCount × 6 as denominator
        long denominator = reach > 0 ? reach : likesCount * 6;
        
        // Handle zero denominator
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        
        // Calculate and return with precision 6, scale 4
        return BigDecimal.valueOf(engagements)
            .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Check if metrics snapshot is duplicate
     * Implements Requirement 8.1, 8.2
     */
    private boolean isDuplicateSnapshot(UUID postId, PostMetrics metrics) {
        Instant thirtySecondsAgo = Instant.now().minusSeconds(30);
        
        Optional<PostMetrics> latestOpt = metricsRepository.findLatestByPostIdSince(postId, thirtySecondsAgo);
        
        if (latestOpt.isEmpty()) {
            return false;
        }
        
        PostMetrics latest = latestOpt.get();
        
        // Compare likes, comments, views counts
        boolean likesMatch = safeEquals(latest.getLikesCount(), metrics.getLikesCount());
        boolean commentsMatch = safeEquals(latest.getCommentsCount(), metrics.getCommentsCount());
        boolean viewsMatch = safeEquals(latest.getViewsCount(), metrics.getViewsCount());
        
        return likesMatch && commentsMatch && viewsMatch;
    }

    /**
     * Safe equals comparison for Long values (handles null)
     */
    private boolean safeEquals(Long a, Long b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Handle collection failure
     */
    private void handleCollectionFailure(InstagramPost post, Exception e) {
        log.error("Collection failed for post {}: {}", post.getId(), e.getMessage());
        
        // After 3 retries (handled by @Retryable), update status to ERROR
        post.setMonitoringStatus(MonitoringStatus.ERROR);
        postRepository.save(post);
    }

    /**
     * Get latest metrics for a post
     */
    @Transactional(readOnly = true)
    public Optional<PostMetrics> getLatestMetrics(UUID postId) {
        return metricsRepository.findLatestByPostId(postId);
    }
}
