package com.socialmedia.instagram.config;

import com.microsoft.playwright.BrowserType;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for Playwright browser automation
 */
@Configuration
public class PlaywrightConfig {

    /**
     * User agents for randomization
     */
    public static final List<String> USER_AGENTS = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15"
    );

    /**
     * Browser launch options with anti-detection flags
     */
    public BrowserType.LaunchOptions getBrowserLaunchOptions() {
        return new BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(Arrays.asList(
                "--disable-blink-features=AutomationControlled",
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--disable-dev-shm-usage",
                "--disable-accelerated-2d-canvas",
                "--disable-gpu"
            ));
    }

    /**
     * Get a random user agent from the list
     */
    public String getRandomUserAgent() {
        int randomIndex = (int) (Math.random() * USER_AGENTS.size());
        return USER_AGENTS.get(randomIndex);
    }
}
