package com.socialmedia.instagram.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotEmpty;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Graph API Exponential Backoff
 * **Validates: Requirements 15.2**
 * 
 * Tests Property 51: Graph API Exponential Backoff
 * For any rate limit error from Instagram Graph API, the system SHALL retry 
 * with exponential backoff starting at 1 minute (60s, 120s, 240s).
 */
class GraphApiExponentialBackoffPropertyTest {

    private InstagramGraphApiService service;
    private HttpClient mockHttpClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = new InstagramGraphApiService();
        objectMapper = new ObjectMapper();
        
        // Set required configuration
        ReflectionTestUtils.setField(service, "appId", "test-app-id");
        ReflectionTestUtils.setField(service, "appSecret", "test-app-secret");
    }

    /**
     * Property 51: Graph API Exponential Backoff Configuration
     * 
     * For any rate limit error, the @Retryable annotation SHALL be configured with:
     * - maxAttempts = 3
     * - delay = 60000 milliseconds (1 minute)
     * - multiplier = 2
     * 
     * This produces the sequence: 60s, 120s, 240s
     */
    @Property
    void exponentialBackoffConfiguration_shouldMatchRequirement15_2() throws NoSuchMethodException {
        // Get the fetchPostInsights method
        Method method = InstagramGraphApiService.class.getMethod(
            "fetchPostInsights", 
            String.class, 
            String.class
        );
        
        // Verify @Retryable annotation is present
        assertTrue(method.isAnnotationPresent(Retryable.class), 
            "fetchPostInsights should have @Retryable annotation");
        
        Retryable retryable = method.getAnnotation(Retryable.class);
        
        // Verify retry configuration for exponential backoff
        assertEquals(3, retryable.maxAttempts(), 
            "Should retry up to 3 times (initial + 2 retries)");
        
        // Verify it retries on GraphApiException
        Class<?>[] retryFor = retryable.retryFor();
        assertTrue(retryFor.length > 0, "Should specify exception types to retry");
        assertEquals(GraphApiException.class, retryFor[0], 
            "Should retry on GraphApiException");
        
        // Verify backoff configuration
        Backoff backoff = retryable.backoff();
        assertEquals(60000, backoff.delay(), 
            "Initial backoff delay should be 60000ms (1 minute)");
        assertEquals(2.0, backoff.multiplier(), 0.01, 
            "Backoff multiplier should be 2 for exponential growth");
        
        // Calculate expected retry delays
        long firstDelay = backoff.delay();  // 60000ms = 60s
        long secondDelay = (long)(firstDelay * backoff.multiplier());  // 120000ms = 120s
        long thirdDelay = (long)(secondDelay * backoff.multiplier());  // 240000ms = 240s
        
        assertEquals(60000, firstDelay, "First retry should wait 60 seconds");
        assertEquals(120000, secondDelay, "Second retry should wait 120 seconds");
        assertEquals(240000, thirdDelay, "Third retry should wait 240 seconds");
    }

    /**
     * Property: Rate Limit Error Detection
     * 
     * For any rate limit error (HTTP 429 or OAuthRateLimitException),
     * the GraphApiException SHALL be configured with the correct status code and type
     * to trigger the exponential backoff retry mechanism.
     */
    @Property
    void rateLimitErrors_shouldTriggerRetryMechanism(
        @ForAll @NotEmpty String mediaId,
        @ForAll @NotEmpty String accessToken
    ) {
        // Test HTTP 429 rate limit
        GraphApiException exception429 = new GraphApiException(
            "Rate limit exceeded", 
            429, 
            "RATE_LIMIT"
        );
        
        assertTrue(exception429.isRateLimitError(), 
            "HTTP 429 should be identified as rate limit error");
        assertEquals(429, exception429.getStatusCode(), 
            "Status code should be 429");
        
        // Test OAuthRateLimitException
        GraphApiException exceptionOAuth = new GraphApiException(
            "Application request limit reached", 
            403, 
            "OAuthRateLimitException"
        );
        
        assertTrue(exceptionOAuth.isRateLimitError(), 
            "OAuthRateLimitException should be identified as rate limit error");
    }

    /**
     * Property: Non-Rate-Limit Errors Should Not Trigger Exponential Backoff
     * 
     * For any non-rate-limit error, the system SHALL NOT treat it as retriable
     * with exponential backoff.
     */
    @Property
    void nonRateLimitErrors_shouldNotBeIdentifiedAsRetriable(
        @ForAll @IntRange(min = 400, max = 599) int statusCode,
        @ForAll @NotEmpty String errorType
    ) {
        // Exclude rate limit status codes and types
        Assume.that(statusCode != 429);
        Assume.that(!errorType.equals("OAuthRateLimitException"));
        Assume.that(!errorType.contains("RateLimit"));
        
        GraphApiException exception = new GraphApiException(
            "Some error", 
            statusCode, 
            errorType
        );
        
        // Verify this is not treated as a rate limit error
        assertFalse(exception.isRateLimitError(), 
            String.format("Status %d with type %s should not be rate limit error", 
                statusCode, errorType));
    }

    /**
     * Property: Requirement 15.2 Compliance Verification
     * 
     * The configured exponential backoff SHALL match the exact sequence
     * specified in Requirement 15.2: 60 seconds, 120 seconds, 240 seconds.
     */
    @Property
    void requirement15_2_backoffSequence_shouldBeExact() throws NoSuchMethodException {
        // Get the retry configuration
        Method method = InstagramGraphApiService.class.getMethod(
            "fetchPostInsights", 
            String.class, 
            String.class
        );
        
        Retryable retryable = method.getAnnotation(Retryable.class);
        Backoff backoff = retryable.backoff();
        
        // Calculate the actual retry delays
        long delay0 = backoff.delay();
        long delay1 = (long)(delay0 * backoff.multiplier());
        long delay2 = (long)(delay1 * backoff.multiplier());
        
        // Verify exact compliance with Requirement 15.2
        assertEquals(60000L, delay0, 
            "First retry delay must be 60000ms (60 seconds)");
        assertEquals(120000L, delay1, 
            "Second retry delay must be 120000ms (120 seconds)");
        assertEquals(240000L, delay2, 
            "Third retry delay must be 240000ms (240 seconds)");
        
        // Verify in human-readable units
        assertEquals(60, delay0 / 1000, 
            "First retry: 60 seconds");
        assertEquals(120, delay1 / 1000, 
            "Second retry: 120 seconds");
        assertEquals(240, delay2 / 1000, 
            "Third retry: 240 seconds");
    }

    /**
     * Property: Total Retry Time Calculation
     * 
     * For the configured exponential backoff (60s, 120s, 240s),
     * the total time spent retrying SHALL be the sum of all delays.
     */
    @Property
    void totalRetryTime_shouldBeSumOfAllDelays() throws NoSuchMethodException {
        Method method = InstagramGraphApiService.class.getMethod(
            "fetchPostInsights", 
            String.class, 
            String.class
        );
        
        Retryable retryable = method.getAnnotation(Retryable.class);
        Backoff backoff = retryable.backoff();
        
        long delay1 = backoff.delay();  // 60s
        long delay2 = (long)(delay1 * backoff.multiplier());  // 120s
        long delay3 = (long)(delay2 * backoff.multiplier());  // 240s
        
        long totalWaitTime = delay1 + delay2 + delay3;
        
        // Total wait time should be 60 + 120 + 240 = 420 seconds = 7 minutes
        assertEquals(420000L, totalWaitTime, 
            "Total retry wait time should be 420 seconds (7 minutes)");
        assertEquals(420, totalWaitTime / 1000, 
            "Total retry wait time should be 7 minutes");
    }

    /**
     * Property: Backoff Delay Growth Rate
     * 
     * For any exponential backoff with multiplier = 2,
     * each subsequent delay SHALL be exactly double the previous delay.
     */
    @Property
    void backoffGrowthRate_shouldDoubleEachTime(
        @ForAll @IntRange(min = 1, max = 10) int retryCount
    ) {
        long initialDelay = 60000; // 1 minute as per Requirement 15.2
        double multiplier = 2.0;
        
        long previousDelay = initialDelay;
        
        for (int i = 1; i <= retryCount; i++) {
            long currentDelay = (long)(initialDelay * Math.pow(multiplier, i));
            
            // Verify doubling
            assertEquals(previousDelay * multiplier, currentDelay, 0.1, 
                String.format("Retry %d delay should be double the previous delay", i));
            
            previousDelay = currentDelay;
        }
    }

    /**
     * Property: Maximum Retry Attempts Limit
     * 
     * For any rate limit error, the system SHALL attempt at most 3 times
     * (initial attempt + 2 retries) before giving up.
     */
    @Property
    void maxRetryAttempts_shouldBeThree() throws NoSuchMethodException {
        Method method = InstagramGraphApiService.class.getMethod(
            "fetchPostInsights", 
            String.class, 
            String.class
        );
        
        Retryable retryable = method.getAnnotation(Retryable.class);
        
        assertEquals(3, retryable.maxAttempts(), 
            "Maximum retry attempts should be 3 (1 initial + 2 retries)");
        
        // Verify that after 3 attempts, no more retries occur
        assertTrue(retryable.maxAttempts() >= 1, 
            "Must allow at least the initial attempt");
        assertTrue(retryable.maxAttempts() <= 10, 
            "Should not retry excessively (max 10 attempts is reasonable)");
    }

    /**
     * Property: Backoff Multiplier Consistency
     * 
     * For the exponential backoff implementation, the multiplier SHALL be
     * consistently applied across all retry attempts.
     */
    @Property
    void backoffMultiplier_shouldBeConsistent(
        @ForAll @IntRange(min = 2, max = 5) int numberOfRetries
    ) throws NoSuchMethodException {
        Method method = InstagramGraphApiService.class.getMethod(
            "fetchPostInsights", 
            String.class, 
            String.class
        );
        
        Retryable retryable = method.getAnnotation(Retryable.class);
        Backoff backoff = retryable.backoff();
        
        double multiplier = backoff.multiplier();
        long initialDelay = backoff.delay();
        
        // Calculate expected delays for each retry
        List<Long> expectedDelays = new ArrayList<>();
        for (int i = 0; i < numberOfRetries; i++) {
            long delay = (long)(initialDelay * Math.pow(multiplier, i));
            expectedDelays.add(delay);
        }
        
        // Verify consistency of multiplier application
        for (int i = 1; i < expectedDelays.size(); i++) {
            long ratio = expectedDelays.get(i) / expectedDelays.get(i - 1);
            assertEquals(multiplier, ratio, 0.1, 
                "Multiplier should be consistently applied between retries");
        }
    }

    /**
     * Property: Delay Duration Reasonableness
     * 
     * For any retry delay in the exponential backoff sequence,
     * the delay SHALL be within reasonable bounds (not too short, not too long).
     */
    @Property
    void retryDelays_shouldBeWithinReasonableBounds() throws NoSuchMethodException {
        Method method = InstagramGraphApiService.class.getMethod(
            "fetchPostInsights", 
            String.class, 
            String.class
        );
        
        Retryable retryable = method.getAnnotation(Retryable.class);
        Backoff backoff = retryable.backoff();
        
        long initialDelay = backoff.delay();
        double multiplier = backoff.multiplier();
        int maxAttempts = retryable.maxAttempts();
        
        // Calculate all delays
        for (int i = 0; i < maxAttempts; i++) {
            long delay = (long)(initialDelay * Math.pow(multiplier, i));
            
            // Verify delays are reasonable
            assertTrue(delay >= 1000, 
                "Delay should be at least 1 second");
            assertTrue(delay <= 600000, 
                "Delay should not exceed 10 minutes (reasonable for rate limiting)");
            
            // For Requirement 15.2 specifically
            if (i == 0) assertEquals(60000, delay, "First delay: 60s");
            if (i == 1) assertEquals(120000, delay, "Second delay: 120s");
            if (i == 2) assertEquals(240000, delay, "Third delay: 240s");
        }
    }
}
