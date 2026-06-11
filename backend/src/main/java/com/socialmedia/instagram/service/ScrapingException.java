package com.socialmedia.instagram.service;

/**
 * Exception thrown when web scraping fails
 */
public class ScrapingException extends Exception {

    public ScrapingException(String message) {
        super(message);
    }

    public ScrapingException(String message, Throwable cause) {
        super(message, cause);
    }
}
