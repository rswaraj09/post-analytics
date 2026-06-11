package com.socialmedia.instagram.service;

import com.socialmedia.instagram.entity.InstagramPost;
import com.socialmedia.instagram.entity.InstagramProfile;
import com.socialmedia.instagram.entity.MonitoringStatus;
import com.socialmedia.instagram.entity.PostType;
import com.socialmedia.instagram.repository.InstagramPostRepository;
import com.socialmedia.instagram.repository.InstagramProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing Instagram posts
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PostService {

    private final InstagramPostRepository postRepository;
    private final InstagramProfileRepository profileRepository;

    // Regex pattern for Instagram post URLs
    private static final Pattern POST_URL_PATTERN = 
        Pattern.compile("https://www\\.instagram\\.com/(p|reel|reels)/([A-Za-z0-9_-]+)/?.*");

    /**
     * Add a new post with URL validation
     * 
     * @param postUrl Instagram post URL
     * @param profileId Profile ID (optional)
     * @return Created post
     * @throws IllegalArgumentException if URL is invalid
     */
    public InstagramPost addPost(String postUrl, UUID profileId) {
        log.info("Adding new post: {}", postUrl);

        // Validate post URL
        if (!isValidPostUrl(postUrl)) {
            throw new IllegalArgumentException("Invalid Instagram post URL: " + postUrl);
        }

        // Check if post already exists
        if (postRepository.existsByPostUrl(postUrl)) {
            throw new IllegalArgumentException("Post URL already exists: " + postUrl);
        }

        // Extract shortcode from URL
        String shortcode = extractShortcode(postUrl);
        if (shortcode == null) {
            throw new IllegalArgumentException("Failed to extract shortcode from URL: " + postUrl);
        }

        // Determine post type from URL
        PostType postType = determinePostType(postUrl);

        // Get profile if provided
        InstagramProfile profile = null;
        if (profileId != null) {
            profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
        }

        // Create post entity
        InstagramPost post = InstagramPost.builder()
            .postUrl(postUrl)
            .postShortcode(shortcode)
            .postType(postType)
            .profile(profile)
            .monitoringStatus(MonitoringStatus.ACTIVE)
            .isCompetitorPost(false)
            .build();

        InstagramPost savedPost = postRepository.save(post);
        log.info("Post created successfully with ID: {}", savedPost.getId());

        return savedPost;
    }

    /**
     * Validate Instagram post URL
     */
    public boolean isValidPostUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return POST_URL_PATTERN.matcher(url).matches();
    }

    /**
     * Extract shortcode from Instagram post URL
     */
    public String extractShortcode(String url) {
        Matcher matcher = POST_URL_PATTERN.matcher(url);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return null;
    }

    /**
     * Determine post type from URL
     */
    private PostType determinePostType(String url) {
        if (url.contains("/reel/") || url.contains("/reels/")) {
            return PostType.REEL;
        }
        return PostType.POST;
    }

    /**
     * Get post by ID
     */
    @Transactional(readOnly = true)
    public Optional<InstagramPost> getPostById(UUID postId) {
        return postRepository.findById(postId);
    }

    /**
     * Get post by URL
     */
    @Transactional(readOnly = true)
    public Optional<InstagramPost> getPostByUrl(String postUrl) {
        return postRepository.findByPostUrl(postUrl);
    }

    /**
     * Get all posts for a profile
     */
    @Transactional(readOnly = true)
    public Page<InstagramPost> getPostsByProfile(UUID profileId, Pageable pageable) {
        return postRepository.findByProfileId(profileId, pageable);
    }

    /**
     * Update post monitoring status
     */
    public InstagramPost updateMonitoringStatus(UUID postId, MonitoringStatus status) {
        log.info("Updating monitoring status for post {} to {}", postId, status);

        InstagramPost post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        post.setMonitoringStatus(status);
        return postRepository.save(post);
    }

    /**
     * Mark post as competitor post
     */
    public InstagramPost markAsCompetitorPost(UUID postId, boolean isCompetitor) {
        log.info("Marking post {} as competitor: {}", postId, isCompetitor);

        InstagramPost post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        post.setIsCompetitorPost(isCompetitor);
        return postRepository.save(post);
    }

    /**
     * Delete post
     */
    public void deletePost(UUID postId) {
        log.info("Deleting post: {}", postId);

        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("Post not found: " + postId);
        }

        postRepository.deleteById(postId);
        log.info("Post deleted successfully: {}", postId);
    }

    /**
     * Update post details
     */
    public InstagramPost updatePost(UUID postId, String caption, String[] hashtags, String[] mentions) {
        log.info("Updating post details: {}", postId);

        InstagramPost post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        if (caption != null) {
            post.setCaption(caption);
        }
        if (hashtags != null) {
            post.setHashtags(hashtags);
        }
        if (mentions != null) {
            post.setMentions(mentions);
        }

        return postRepository.save(post);
    }
}
