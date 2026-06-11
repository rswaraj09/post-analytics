package com.socialmedia.instagram.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmedia.instagram.dto.PostInsights;
import com.socialmedia.instagram.dto.TokenValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Implementation of GraphApiService using Instagram Graph API v18.0
 * Implements Requirements 5.1, 5.2, 5.4, 5.5, 5.6, 15.2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstagramGraphApiService implements GraphApiService {

    private static final String GRAPH_API_VERSION = "v18.0";
    private static final String GRAPH_API_BASE_URL = "https://graph.facebook.com/" + GRAPH_API_VERSION;
    
    @Value("${instagram.graph-api.app-id:}")
    private String appId;
    
    @Value("${instagram.graph-api.app-secret:}")
    private String appSecret;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * Fetch post insights from Instagram Graph API
     * Implements Requirement 5.1, 5.2
     * 
     * Uses exponential backoff retry for rate limit errors (Requirement 15.2)
     */
    @Override
    @Retryable(
        retryFor = {GraphApiException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 60000, multiplier = 2) // 1 min, 2 min, 4 min
    )
    public PostInsights fetchPostInsights(String mediaId, String accessToken) 
        throws GraphApiException {
        
        log.info("Fetching insights for media ID: {}", mediaId);
        
        if (mediaId == null || mediaId.trim().isEmpty()) {
            throw new GraphApiException("Media ID cannot be null or empty");
        }
        
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new GraphApiException("Access token cannot be null or empty");
        }
        
        try {
            // Build insights request
            // Request metrics: likes, comments, video_views, reach, impressions, saved, shares
            String metricsParam = "engagement,impressions,reach,saved,video_views";
            String url = String.format("%s/%s/insights?metric=%s&access_token=%s",
                GRAPH_API_BASE_URL,
                URLEncoder.encode(mediaId, StandardCharsets.UTF_8),
                URLEncoder.encode(metricsParam, StandardCharsets.UTF_8),
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            log.debug("Graph API insights response status: {}", response.statusCode());
            
            // Handle error responses
            // Handle error responses
            if (response.statusCode() >= 400) {
                handleErrorResponse(response);
                // This line is unreachable because handleErrorResponse always throws
                return null;
            }
            
            // Parse insights response
            return parseInsightsResponse(mediaId, response.body(), accessToken);
            
        } catch (IOException | InterruptedException e) {
            log.error("Failed to fetch insights for media {}: {}", mediaId, e.getMessage());
            throw new GraphApiException("Network error while fetching insights", e);
        }
    }

    /**
     * Parse insights response and combine with basic metrics
     */
    private PostInsights parseInsightsResponse(String mediaId, String responseBody, String accessToken) 
        throws GraphApiException {
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            
            PostInsights.PostInsightsBuilder builder = PostInsights.builder()
                .mediaId(mediaId);
            
            // Parse insights metrics
            if (data.isArray()) {
                for (JsonNode metric : data) {
                    String name = metric.path("name").asText();
                    long value = metric.path("values")
                        .path(0)
                        .path("value")
                        .asLong(0);
                    
                    switch (name) {
                        case "engagement":
                            // Engagement is the sum of likes, comments, saves, shares
                            // We'll fetch likes and comments separately
                            break;
                        case "impressions":
                            builder.impressions(value);
                            log.debug("Impressions: {}", value);
                            break;
                        case "reach":
                            builder.reach(value);
                            log.debug("Reach: {}", value);
                            break;
                        case "saved":
                            builder.saved(value);
                            log.debug("Saved: {}", value);
                            break;
                        case "video_views":
                            builder.videoViews(value);
                            log.debug("Video views: {}", value);
                            break;
                    }
                }
            }
            
            // Fetch basic metrics (likes, comments) from media object
            fetchBasicMetrics(mediaId, accessToken, builder);
            
            PostInsights insights = builder.build();
            
            log.info("Successfully fetched insights for media {}. Reach: {}, Impressions: {}, Likes: {}, Comments: {}",
                mediaId, insights.getReach(), insights.getImpressions(), 
                insights.getLikesCount(), insights.getCommentsCount());
            
            return insights;
            
        } catch (Exception e) {
            log.error("Failed to parse insights response: {}", e.getMessage());
            throw new GraphApiException("Failed to parse insights response", e);
        }
    }

    /**
     * Fetch basic metrics (likes, comments) from media object
     */
    private void fetchBasicMetrics(String mediaId, String accessToken, 
                                   PostInsights.PostInsightsBuilder builder) 
        throws GraphApiException {
        
        try {
            String url = String.format("%s/%s?fields=like_count,comments_count&access_token=%s",
                GRAPH_API_BASE_URL,
                URLEncoder.encode(mediaId, StandardCharsets.UTF_8),
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                long likes = root.path("like_count").asLong(0);
                long comments = root.path("comments_count").asLong(0);
                
                builder.likesCount(likes);
                builder.commentsCount(comments);
                
                log.debug("Likes: {}, Comments: {}", likes, comments);
            } else {
                log.warn("Failed to fetch basic metrics, status: {}", response.statusCode());
            }
            
        } catch (Exception e) {
            log.warn("Failed to fetch basic metrics: {}", e.getMessage());
            // Don't throw, just log - insights are more important
        }
    }

    /**
     * Validate Graph API access token
     * Implements Requirement 5.4
     */
    @Override
    public TokenValidation validateToken(String accessToken) 
        throws GraphApiException {
        
        log.info("Validating Graph API token");
        
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new GraphApiException("Access token cannot be null or empty");
        }
        
        try {
            String url = String.format("%s/debug_token?input_token=%s&access_token=%s|%s",
                GRAPH_API_BASE_URL,
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                URLEncoder.encode(appId, StandardCharsets.UTF_8),
                URLEncoder.encode(appSecret, StandardCharsets.UTF_8)
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                log.error("Token validation failed with status: {}", response.statusCode());
                return TokenValidation.builder()
                    .isValid(false)
                    .errorMessage("Token validation failed")
                    .build();
            }
            
            return parseTokenValidationResponse(response.body());
            
        } catch (IOException | InterruptedException e) {
            log.error("Failed to validate token: {}", e.getMessage());
            throw new GraphApiException("Network error while validating token", e);
        }
    }

    /**
     * Parse token validation response
     */
    private TokenValidation parseTokenValidationResponse(String responseBody) 
        throws GraphApiException {
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            
            boolean isValid = data.path("is_valid").asBoolean(false);
            String appId = data.path("app_id").asText(null);
            String userId = data.path("user_id").asText(null);
            
            // Parse expiration timestamp
            long expiresAtTimestamp = data.path("expires_at").asLong(0);
            Instant expiresAt = expiresAtTimestamp > 0 
                ? Instant.ofEpochSecond(expiresAtTimestamp) 
                : null;
            
            // Parse issued at timestamp
            long issuedAtTimestamp = data.path("issued_at").asLong(0);
            Instant issuedAt = issuedAtTimestamp > 0 
                ? Instant.ofEpochSecond(issuedAtTimestamp) 
                : null;
            
            // Parse scopes
            JsonNode scopesNode = data.path("scopes");
            String[] scopes = null;
            if (scopesNode.isArray()) {
                scopes = new String[scopesNode.size()];
                for (int i = 0; i < scopesNode.size(); i++) {
                    scopes[i] = scopesNode.get(i).asText();
                }
            }
            
            TokenValidation validation = TokenValidation.builder()
                .isValid(isValid)
                .appId(appId)
                .userId(userId)
                .expiresAt(expiresAt)
                .issuedAt(issuedAt)
                .scopes(scopes)
                .build();
            
            log.info("Token validation result: isValid={}, expiresAt={}", isValid, expiresAt);
            
            return validation;
            
        } catch (Exception e) {
            log.error("Failed to parse token validation response: {}", e.getMessage());
            throw new GraphApiException("Failed to parse token validation response", e);
        }
    }

    /**
     * Refresh long-lived access token
     * Implements Requirement 5.4
     */
    @Override
    public String refreshAccessToken(String currentToken) 
        throws GraphApiException {
        
        log.info("Refreshing Graph API access token");
        
        if (currentToken == null || currentToken.trim().isEmpty()) {
            throw new GraphApiException("Current token cannot be null or empty");
        }
        
        if (appId == null || appId.trim().isEmpty() || 
            appSecret == null || appSecret.trim().isEmpty()) {
            throw new GraphApiException("App ID and App Secret must be configured");
        }
        
        try {
            String url = String.format("%s/oauth/access_token?grant_type=fb_exchange_token&client_id=%s&client_secret=%s&fb_exchange_token=%s",
                GRAPH_API_BASE_URL,
                URLEncoder.encode(appId, StandardCharsets.UTF_8),
                URLEncoder.encode(appSecret, StandardCharsets.UTF_8),
                URLEncoder.encode(currentToken, StandardCharsets.UTF_8)
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                log.error("Token refresh failed with status: {}", response.statusCode());
                handleErrorResponse(response);
                // This line is unreachable because handleErrorResponse always throws
                return null;
            }
            
            return parseTokenRefreshResponse(response.body());
            
        } catch (IOException | InterruptedException e) {
            log.error("Failed to refresh token: {}", e.getMessage());
            throw new GraphApiException("Network error while refreshing token", e);
        }
    }

    /**
     * Parse token refresh response
     */
    private String parseTokenRefreshResponse(String responseBody) 
        throws GraphApiException {
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String newToken = root.path("access_token").asText(null);
            
            if (newToken == null || newToken.isEmpty()) {
                throw new GraphApiException("No access token in refresh response");
            }
            
            long expiresIn = root.path("expires_in").asLong(0);
            log.info("Token refreshed successfully, expires in {} seconds", expiresIn);
            
            return newToken;
            
        } catch (Exception e) {
            log.error("Failed to parse token refresh response: {}", e.getMessage());
            throw new GraphApiException("Failed to parse token refresh response", e);
        }
    }

    /**
     * Handle error responses from Graph API
     * Implements Requirement 5.6, 15.2
     */
    private void handleErrorResponse(HttpResponse<String> response) 
        throws GraphApiException {
        
        int statusCode = response.statusCode();
        String body = response.body();
        
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            
            String message = error.path("message").asText("Unknown error");
            int errorCode = error.path("code").asInt(0);
            String errorType = error.path("type").asText(null);
            
            log.error("Graph API error: code={}, type={}, message={}", 
                errorCode, errorType, message);
            
            // Handle rate limiting (Requirement 15.2)
            if (statusCode == 429 || "OAuthException".equals(errorType)) {
                throw new GraphApiException(
                    "Rate limit exceeded: " + message, 
                    statusCode, 
                    errorType != null ? errorType : "RATE_LIMIT"
                );
            }
            
            // Handle token expiration (Requirement 5.5)
            if (errorCode == 190 || "OAuthException".equals(errorType)) {
                throw new GraphApiException(
                    "Token expired or invalid: " + message, 
                    statusCode, 
                    errorType != null ? errorType : "TOKEN_EXPIRED"
                );
            }
            
            throw new GraphApiException(
                message, 
                statusCode, 
                errorType != null ? errorType : "UNKNOWN"
            );
            
        } catch (Exception e) {
            if (e instanceof GraphApiException) {
                throw (GraphApiException) e;
            }
            throw new GraphApiException(
                "HTTP " + statusCode + ": " + body, 
                statusCode, 
                "HTTP_ERROR"
            );
        }
    }

    /**
     * Extract media ID from Instagram post shortcode
     * Implements Requirement 5.1
     * 
     * Note: This is a simplified implementation that returns the shortcode as-is.
     * In production, you would need to:
     * 1. Get the Instagram Business Account ID
     * 2. Call GET /{ig-user-id}/media?fields=id,shortcode
     * 3. Find the media with matching shortcode
     * 4. Return the numeric media ID
     */
    @Override
    public String getMediaIdFromShortcode(String shortcode, String accessToken) 
        throws GraphApiException {
        
        log.info("Getting media ID for shortcode: {}", shortcode);
        
        if (shortcode == null || shortcode.trim().isEmpty()) {
            throw new GraphApiException("Shortcode cannot be null or empty");
        }
        
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new GraphApiException("Access token cannot be null or empty");
        }
        
        // For now, return the shortcode as-is
        // The Instagram Graph API uses numeric media IDs internally,
        // but the implementation depends on having the Instagram Business Account ID
        // which should be obtained during profile setup
        
        return shortcode;
    }
}
