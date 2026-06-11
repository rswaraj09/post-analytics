package com.socialmedia.instagram.service;

import com.socialmedia.instagram.dto.PostInsights;
import com.socialmedia.instagram.dto.TokenValidation;

/**
 * Service interface for Instagram Graph API integration
 * Provides access to authentic Instagram metrics for business accounts
 */
public interface GraphApiService {
    
    /**
     * Fetch post insights from Instagram Graph API
     * 
     * @param mediaId Instagram media ID
     * @param accessToken Valid Instagram access token
     * @return Post insights with real metrics
     * @throws GraphApiException if API call fails
     */
    PostInsights fetchPostInsights(String mediaId, String accessToken) throws GraphApiException;
    
    /**
     * Validate Instagram access token
     * 
     * @param accessToken Token to validate
     * @return TokenValidation result with validity details
     * @throws GraphApiException if validation fails
     */
    TokenValidation validateToken(String accessToken) throws GraphApiException;
    
    /**
     * Refresh long-lived access token
     * 
     * @param currentToken Current access token
     * @return New refreshed token
     * @throws GraphApiException if refresh fails
     */
    String refreshAccessToken(String currentToken) throws GraphApiException;
    
    /**
     * Extract media ID from Instagram post URL or shortcode
     * 
     * @param shortcode Instagram post shortcode
     * @param accessToken Valid access token
     * @return Media ID for use with insights API
     * @throws GraphApiException if extraction fails
     */
    String getMediaIdFromShortcode(String shortcode, String accessToken) throws GraphApiException;
}
