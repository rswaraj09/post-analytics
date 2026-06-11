package com.socialmedia.instagram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for scraped Instagram post data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapedPostData {
    private Long likesCount;
    private Long commentsCount;
    private Long viewsCount;
    private String caption;
    private String mediaUrl;
}
