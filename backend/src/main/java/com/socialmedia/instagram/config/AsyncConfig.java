package com.socialmedia.instagram.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for async processing, retry, and scheduling
 */
@Configuration
@EnableAsync
@EnableRetry
@EnableScheduling
public class AsyncConfig {
}
