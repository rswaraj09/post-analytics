package com.socialmedia.instagram.controller;

import com.socialmedia.instagram.dto.ScrapedPostData;
import com.socialmedia.instagram.service.MetricsCollectionService;
import com.socialmedia.instagram.service.ScrapingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo controller for testing scraping functionality without authentication
 * WARNING: This controller should be removed or secured in production!
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final ScrapingService scrapingService;
    private final MetricsCollectionService metricsCollectionService;

    /**
     * Demo endpoint to scrape an Instagram post
     * POST /api/demo/scrape
     * 
     * Request body: { "postUrl": "https://www.instagram.com/p/SHORTCODE/" }
     * 
     * This endpoint demonstrates the core scraping functionality without requiring
     * authentication or database setup.
     */
    @PostMapping("/scrape")
    public ResponseEntity<?> scrapePost(@RequestBody DemoScrapeRequest request) {
        try {
            log.info("Demo scrape request for: {}", request.postUrl());
            
            // Validate URL
            if (request.postUrl() == null || request.postUrl().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Post URL is required"));
            }
            
            // Scrape the post
            ScrapedPostData scrapedData = scrapingService.scrapePost(request.postUrl());
            
            // Calculate estimated metrics
            long likesCount = scrapedData.getLikesCount() != null ? scrapedData.getLikesCount() : 0L;
            long commentsCount = scrapedData.getCommentsCount() != null ? scrapedData.getCommentsCount() : 0L;
            long viewsCount = scrapedData.getViewsCount() != null ? scrapedData.getViewsCount() : 0L;
            
            long estimatedReach = metricsCollectionService.calculateEstimatedReach(likesCount);
            long estimatedImpressions = metricsCollectionService.calculateEstimatedImpressions(estimatedReach);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("postUrl", request.postUrl());
            response.put("likesCount", likesCount);
            response.put("commentsCount", commentsCount);
            response.put("viewsCount", viewsCount);
            response.put("estimatedReach", estimatedReach);
            response.put("estimatedImpressions", estimatedImpressions);
            response.put("engagementRate", metricsCollectionService.calculateEngagementRate(
                likesCount, commentsCount, estimatedReach));
            response.put("caption", scrapedData.getCaption());
            response.put("mediaUrl", scrapedData.getMediaUrl());
            response.put("message", "Successfully scraped Instagram post");
            response.put("note", "Reach and impressions are estimated. For accurate data, use Instagram Graph API.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Demo scrape failed: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to scrape post");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("errorType", e.getClass().getSimpleName());
            errorResponse.put("postUrl", request.postUrl());
            
            // Add root cause if available
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            if (rootCause != e) {
                errorResponse.put("rootCause", rootCause.getMessage());
                errorResponse.put("rootCauseType", rootCause.getClass().getSimpleName());
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
        }
    }
    
    /**
     * Health check endpoint
     * GET /api/demo/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Demo API is running");
        response.put("scraping", "Playwright scraping service is available");
        response.put("rateLimited", scrapingService.isRateLimited());
        
        return ResponseEntity.ok(response);
    }
    
    // DTO
    public record DemoScrapeRequest(String postUrl) {}
}
