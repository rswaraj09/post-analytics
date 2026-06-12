package com.socialmedia.instagram.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.socialmedia.instagram.config.PlaywrightConfig;
import com.socialmedia.instagram.dto.ScrapedPostData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of ScrapingService using Playwright for web scraping
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaywrightScrapingService implements ScrapingService {

    private final PlaywrightConfig playwrightConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean rateLimited = new AtomicBoolean(false);

    private static final int MAX_RETRIES = 3;
    private static final int NAVIGATION_TIMEOUT = 30000; // 30 seconds

    /**
     * Scrape Instagram post with retry logic
     * Implements exponential backoff: 5s, 10s, 20s
     */
    @Override
    @Retryable(
        retryFor = {ScrapingException.class},
        maxAttempts = MAX_RETRIES,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public ScrapedPostData scrapePost(String postUrl) throws ScrapingException {
        log.info("Starting to scrape post: {}", postUrl);

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        Page page = null;

        try {
            // Initialize Playwright
            playwright = Playwright.create();
            
            // Launch browser with anti-detection settings
            browser = playwright.chromium().launch(playwrightConfig.getBrowserLaunchOptions());
            
            // Create stealth context
            context = createStealthContext(browser);
            page = context.newPage();

            // Intercept GraphQL API responses
            List<String> graphQLResponses = new ArrayList<>();
            page.onResponse(response -> {
                String url = response.url();
                if (url.contains("/graphql/query") || url.contains("/api/v1/")) {
                    try {
                        String body = response.text();
                        if (body != null && !body.isEmpty()) {
                            graphQLResponses.add(body);
                            log.debug("Intercepted GraphQL response from: {}", url);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to capture response body: {}", e.getMessage());
                    }
                }
            });

            // Navigate to post URL
            log.debug("Navigating to: {}", postUrl);
            Response response = page.navigate(postUrl, new Page.NavigateOptions()
                .setTimeout(NAVIGATION_TIMEOUT));

            if (response == null) {
                throw new ScrapingException("No response received from Instagram");
            }

            int statusCode = response.status();
            log.debug("Response status code: {}", statusCode);

            // Handle rate limiting
            if (statusCode == 429) {
                rateLimited.set(true);
                throw new ScrapingException("Rate limited by Instagram (HTTP 429)");
            }

            if (statusCode >= 400) {
                throw new ScrapingException("HTTP error: " + statusCode);
            }

            // Wait for content to load
            page.waitForLoadState(LoadState.NETWORKIDLE);
            
            // Check for login wall
            boolean requiresLogin = false;
            try {
                // Check if Instagram is showing login prompt
                requiresLogin = page.locator("text=Log in to continue").isVisible(new Locator.IsVisibleOptions().setTimeout(2000)) ||
                               page.locator("text=Sign up").isVisible(new Locator.IsVisibleOptions().setTimeout(2000)) ||
                               page.locator("a[href*='/accounts/login']").isVisible(new Locator.IsVisibleOptions().setTimeout(2000));
                
                if (requiresLogin) {
                    log.warn("Instagram is requiring login to view this post");
                }
            } catch (Exception e) {
                log.debug("Could not check for login wall");
            }
            
            // Give Instagram time to render
            page.waitForTimeout(2000);

            // Try to parse from GraphQL responses first
            ScrapedPostData data = parseFromGraphQL(graphQLResponses);

            // Fallback to DOM parsing if GraphQL parsing fails
            if (data == null || data.getLikesCount() == null) {
                log.info("GraphQL parsing failed or incomplete, falling back to DOM parsing");
                data = parseFromDOM(page);
            }

            // Check if we got any real data (not just nulls or zeros)
            boolean hasRealData = data != null && 
                                  data.getLikesCount() != null && 
                                  data.getLikesCount() > 0;
            
            if (!hasRealData) {
                // Scraping failed - Instagram requires login to view post metrics
                log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.error("⚠️  Could not scrape real data from Instagram");
                log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.error("Reason: Instagram now requires login to view post metrics");
                log.error("Solution: Use Instagram Graph API with business account access token");
                log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                throw new ScrapingException(
                    "Instagram requires login to view post metrics. " +
                    "Please add this post to a business profile with a valid Graph API token, " +
                    "or ensure the post is publicly accessible without login."
                );
            }

            rateLimited.set(false);
            log.info("Successfully scraped post. Likes: {}, Comments: {}, Views: {}", 
                data.getLikesCount(), data.getCommentsCount(), data.getViewsCount());

            return data;

        } catch (PlaywrightException e) {
            log.error("Playwright error while scraping: {}", e.getMessage());
            throw new ScrapingException("Playwright error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while scraping: {}", e.getMessage(), e);
            throw new ScrapingException("Scraping failed: " + e.getMessage(), e);
        } finally {
            // Cleanup resources
            cleanup(page, context, browser, playwright);
        }
    }

    /**
     * Create browser context with stealth settings
     */
    private BrowserContext createStealthContext(Browser browser) {
        String randomUserAgent = playwrightConfig.getRandomUserAgent();
        log.debug("Using user agent: {}", randomUserAgent);

        Browser.NewContextOptions options = new Browser.NewContextOptions()
            .setUserAgent(randomUserAgent)
            .setViewportSize(1920, 1080)
            .setLocale("en-US")
            .setTimezoneId("America/New_York")
            .setPermissions(List.of("geolocation"));

        BrowserContext context = browser.newContext(options);

        // Remove webdriver property
        context.addInitScript(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        );

        return context;
    }

    /**
     * Parse metrics from intercepted GraphQL responses
     */
    private ScrapedPostData parseFromGraphQL(List<String> responses) {
        for (String responseBody : responses) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode data = root.path("data");

                // Try different GraphQL response structures
                JsonNode media = data.path("xdt_shortcode_media");
                if (media.isMissingNode()) {
                    media = data.path("shortcode_media");
                }

                if (!media.isMissingNode()) {
                    JsonNode edgeMedia = media.path("edge_media_preview_like");
                    Long likes = edgeMedia.path("count").asLong(0L);

                    JsonNode edgeComments = media.path("edge_media_to_parent_comment");
                    if (edgeComments.isMissingNode()) {
                        edgeComments = media.path("edge_media_to_comment");
                    }
                    Long comments = edgeComments.path("count").asLong(0L);

                    Long views = media.path("video_view_count").asLong(0L);

                    String caption = media.path("edge_media_to_caption")
                        .path("edges")
                        .path(0)
                        .path("node")
                        .path("text")
                        .asText(null);

                    String mediaUrl = media.path("display_url").asText(null);

                    if (likes > 0 || comments > 0) {
                        return ScrapedPostData.builder()
                            .likesCount(likes)
                            .commentsCount(comments)
                            .viewsCount(views)
                            .caption(caption)
                            .mediaUrl(mediaUrl)
                            .build();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse GraphQL response: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Parse metrics from page DOM (fallback method)
     */
    private ScrapedPostData parseFromDOM(Page page) {
        try {
            // Save screenshot and HTML for debugging
            try {
                page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get("debug-screenshot.png")));
                String html = page.content();
                java.nio.file.Files.writeString(java.nio.file.Paths.get("debug-page.html"), html);
                log.info("Saved debug screenshot and HTML");
            } catch (Exception e) {
                log.warn("Could not save debug files: {}", e.getMessage());
            }

            ScrapedPostData.ScrapedPostDataBuilder builder = ScrapedPostData.builder();

            // Try multiple selector strategies for likes
            Long likes = null;
            try {
                // Strategy 1: Look for aria-label="like"
                String likesText = page.locator("button[aria-label*='like'], svg[aria-label*='like']")
                    .first()
                    .locator("..")
                    .textContent(new Locator.TextContentOptions().setTimeout(2000));
                likes = parseLongFromText(likesText);
                log.debug("Extracted likes (strategy 1): {}", likes);
            } catch (Exception e) {
                log.debug("Likes strategy 1 failed");
            }

            if (likes == null || likes == 0) {
                try {
                    // Strategy 2: Look for text containing "likes"
                    String likesText = page.locator("section span:has-text('like')").first()
                        .textContent(new Locator.TextContentOptions().setTimeout(2000));
                    likes = parseLongFromText(likesText);
                    log.debug("Extracted likes (strategy 2): {}", likes);
                } catch (Exception e) {
                    log.debug("Likes strategy 2 failed");
                }
            }

            if (likes != null) {
                builder.likesCount(likes);
            }

            // Try multiple selector strategies for comments
            Long comments = null;
            try {
                // Strategy 1: Look for aria-label="comment"
                String commentsText = page.locator("button[aria-label*='comment'], svg[aria-label*='comment']")
                    .first()
                    .locator("..")
                    .textContent(new Locator.TextContentOptions().setTimeout(2000));
                comments = parseLongFromText(commentsText);
                log.debug("Extracted comments (strategy 1): {}", comments);
            } catch (Exception e) {
                log.debug("Comments strategy 1 failed");
            }

            if (comments == null || comments == 0) {
                try {
                    // Strategy 2: Look for text containing "comment"
                    String commentsText = page.locator("section span:has-text('comment')").first()
                        .textContent(new Locator.TextContentOptions().setTimeout(2000));
                    comments = parseLongFromText(commentsText);
                    log.debug("Extracted comments (strategy 2): {}", comments);
                } catch (Exception e) {
                    log.debug("Comments strategy 2 failed");
                }
            }

            if (comments != null) {
                builder.commentsCount(comments);
            }

            // Try to extract views count (for videos/reels)
            try {
                String viewsText = page.locator("span:has-text('view')").first()
                    .textContent(new Locator.TextContentOptions().setTimeout(2000));
                if (viewsText != null) {
                    Long views = parseLongFromText(viewsText);
                    builder.viewsCount(views);
                    log.debug("Extracted views from DOM: {}", views);
                }
            } catch (Exception e) {
                log.debug("Could not extract views from DOM");
            }

            // Try to extract caption
            try {
                String caption = page.locator("h1").first()
                    .textContent(new Locator.TextContentOptions().setTimeout(2000));
                if (caption != null && !caption.trim().isEmpty()) {
                    builder.caption(caption.trim());
                    log.debug("Extracted caption (first 50 chars): {}", 
                        caption.length() > 50 ? caption.substring(0, 50) + "..." : caption);
                }
            } catch (Exception e) {
                log.debug("Could not extract caption from DOM");
            }

            // Try to extract media URL
            try {
                String mediaUrl = page.locator("article img[src*='instagram']").first()
                    .getAttribute("src", new Locator.GetAttributeOptions().setTimeout(2000));
                if (mediaUrl != null) {
                    builder.mediaUrl(mediaUrl);
                    log.debug("Extracted media URL");
                }
            } catch (Exception e) {
                log.debug("Could not extract media URL from DOM");
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to parse DOM: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse long value from text like "1,234 likes" or "1.2M views"
     */
    private Long parseLongFromText(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }

        // Remove non-numeric characters except . and ,
        String cleaned = text.replaceAll("[^0-9.,KMB]", "").trim();

        try {
            // Handle K (thousands), M (millions), B (billions)
            if (cleaned.contains("K")) {
                double value = Double.parseDouble(cleaned.replace("K", ""));
                return (long) (value * 1000);
            } else if (cleaned.contains("M")) {
                double value = Double.parseDouble(cleaned.replace("M", ""));
                return (long) (value * 1_000_000);
            } else if (cleaned.contains("B")) {
                double value = Double.parseDouble(cleaned.replace("B", ""));
                return (long) (value * 1_000_000_000);
            } else {
                // Remove commas and parse
                return Long.parseLong(cleaned.replace(",", ""));
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse number from text: {}", text);
            return 0L;
        }
    }

    /**
     * Cleanup Playwright resources
     */
    private void cleanup(Page page, BrowserContext context, Browser browser, Playwright playwright) {
        try {
            if (page != null) page.close();
        } catch (Exception e) {
            log.warn("Error closing page: {}", e.getMessage());
        }
        try {
            if (context != null) context.close();
        } catch (Exception e) {
            log.warn("Error closing context: {}", e.getMessage());
        }
        try {
            if (browser != null) browser.close();
        } catch (Exception e) {
            log.warn("Error closing browser: {}", e.getMessage());
        }
        try {
            if (playwright != null) playwright.close();
        } catch (Exception e) {
            log.warn("Error closing playwright: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRateLimited() {
        return rateLimited.get();
    }
}
