package com.socialmedia.instagram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Instagram Graph API post insights response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostInsights {
    
    private String mediaId;
    private Long likesCount;
    private Long commentsCount;
    private Long videoViews;
    private Long reach;
    private Long impressions;
    private Long saved;
    private Long shares;
    private Long profileVisits;
    
    /**
     * Check if this insights data has valid metrics
     */
    public boolean hasValidMetrics() {
        return likesCount != null || commentsCount != null || 
               reach != null || impressions != null;
    }
}
