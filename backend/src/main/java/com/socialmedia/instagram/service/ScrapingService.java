package com.socialmedia.instagram.service;

import com.socialmedia.instagram.dto.ScrapedPostData;

/**
 * Service interface for web scraping Instagram posts
 */
public interface ScrapingService {

    /**
     * Scrape Instagram post metrics
     * 
     * @param postUrl Instagram post URL
     * @return Scraped post data
     * @throws ScrapingException if scraping fails
     */
    ScrapedPostData scrapePost(String postUrl) throws ScrapingException;

    /**
     * Check if the scraping service is currently rate-limited
     * 
     * @return true if rate limited
     */
    boolean isRateLimited();
}
