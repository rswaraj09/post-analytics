package com.socialmedia.instagram.controller;

import com.socialmedia.instagram.entity.InstagramPost;
import com.socialmedia.instagram.entity.MonitoringStatus;
import com.socialmedia.instagram.entity.PostMetrics;
import com.socialmedia.instagram.service.MetricsCollectionService;
import com.socialmedia.instagram.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for Instagram post management
 * Demonstrates role-based access control implementation
 * Requirements 13.1, 13.2, 13.3, 13.4, 13.5, 13.6
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final MetricsCollectionService metricsCollectionService;
    
    /**
     * Create new post and immediately fetch metrics
     * POST /api/posts
     * Requirement 13.2: ADMIN can do all operations
     * Requirement 13.3: MANAGER can add posts
     * Requirement 3.1, 3.4: Create post and trigger immediate metrics collection
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> createPost(@RequestBody CreatePostRequest request) {
        try {
            log.info("Creating new post: {}", request.postUrl());
            
            // Create post
            InstagramPost post = postService.addPost(request.postUrl(), request.profileId());
            
            // Immediately trigger metrics collection
            try {
                PostMetrics metrics = metricsCollectionService.collectMetrics(post.getId());
                
                Map<String, Object> response = new HashMap<>();
                response.put("post", post);
                response.put("metrics", metrics);
                response.put("message", "Post created and metrics collected successfully");
                
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } catch (Exception e) {
                log.warn("Post created but metrics collection failed: {}", e.getMessage());
                
                Map<String, Object> response = new HashMap<>();
                response.put("post", post);
                response.put("message", "Post created but metrics collection failed: " + e.getMessage());
                
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            }
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create post: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create post: " + e.getMessage()));
        }
    }
    
    /**
     * Get post details with latest metrics
     * GET /api/posts/{id}
     * Requirement 3.5: Return post metadata with latest metrics snapshot
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPost(@PathVariable UUID id) {
        try {
            Optional<InstagramPost> postOpt = postService.getPostById(id);
            
            if (postOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            InstagramPost post = postOpt.get();
            Optional<PostMetrics> latestMetrics = metricsCollectionService.getLatestMetrics(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("post", post);
            response.put("latestMetrics", latestMetrics.orElse(null));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get post: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get post: " + e.getMessage()));
        }
    }
    
    /**
     * List all posts - all authenticated users can view
     * GET /api/posts
     * Requirement 13.5: VIEWER can view data
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listPosts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) UUID profileId
    ) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            
            Page<InstagramPost> posts;
            if (profileId != null) {
                posts = postService.getPostsByProfile(profileId, pageRequest);
            } else {
                // This would need to be implemented in PostService
                return ResponseEntity.ok(Map.of("message", "List all posts - to be implemented"));
            }
            
            return ResponseEntity.ok(posts);
            
        } catch (Exception e) {
            log.error("Failed to list posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list posts: " + e.getMessage()));
        }
    }
    
    /**
     * Update post monitoring status
     * PUT /api/posts/{id}/monitoring-status
     * Requirement 3.6: Update monitoring status
     */
    @PutMapping("/{id}/monitoring-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> updateMonitoringStatus(
        @PathVariable UUID id,
        @RequestBody UpdateMonitoringStatusRequest request
    ) {
        try {
            InstagramPost post = postService.updateMonitoringStatus(id, request.status());
            return ResponseEntity.ok(post);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update monitoring status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update monitoring status: " + e.getMessage()));
        }
    }
    
    /**
     * Mark post as competitor post
     * PUT /api/posts/{id}/competitor-flag
     * Requirement 3.7: Mark as competitor post
     */
    @PutMapping("/{id}/competitor-flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> markAsCompetitorPost(
        @PathVariable UUID id,
        @RequestBody MarkCompetitorRequest request
    ) {
        try {
            InstagramPost post = postService.markAsCompetitorPost(id, request.isCompetitor());
            return ResponseEntity.ok(post);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to mark as competitor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to mark as competitor: " + e.getMessage()));
        }
    }
    
    /**
     * Delete post - requires ADMIN role only
     * DELETE /api/posts/{id}
     * Requirement 13.2: ADMIN can do all operations
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deletePost(@PathVariable UUID id) {
        try {
            postService.deletePost(id);
            return ResponseEntity.ok(Map.of("message", "Post deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete post: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete post: " + e.getMessage()));
        }
    }
    
    /**
     * Export posts data - requires ADMIN, MANAGER, or ANALYST role
     * GET /api/posts/export
     * Requirement 13.3: MANAGER can export data
     * Requirement 13.4: ANALYST can export data
     * Requirement 13.5: VIEWER cannot export data
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    public ResponseEntity<String> exportPosts() {
        return ResponseEntity.ok("Posts exported (ADMIN, MANAGER, or ANALYST only)");
    }
    
    // DTOs
    public record CreatePostRequest(String postUrl, UUID profileId) {}
    public record UpdateMonitoringStatusRequest(MonitoringStatus status) {}
    public record MarkCompetitorRequest(boolean isCompetitor) {}
}
