package com.socialmedia.instagram.security;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for JWT token generation and validation
 */
class JwtTokenProviderPropertyTest {
    
    // Constants from application.properties
    private static final long ACCESS_TOKEN_EXPIRATION = 900000L; // 15 minutes in milliseconds
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 days in milliseconds
    private static final String TEST_SECRET = "test-secret-key-for-jwt-token-generation-min-256-bits-required";
    
    private JwtTokenProvider createProvider() {
        return new JwtTokenProvider(TEST_SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
    }
    
    /**
     * Property 1: JWT Token Generation
     * 
     * **Validates: Requirements 1.1**
     * 
     * For any authenticated user with valid credentials, the system SHALL generate 
     * a JWT access token with expiration of exactly 15 minutes and a refresh token 
     * with expiration of exactly 7 days.
     */
    @Property
    @Label("Property 1: JWT Token Generation - Access and refresh tokens have correct expiration")
    void jwtTokenGenerationProperty(
            @ForAll("uuids") UUID userId,
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String email,
            @ForAll("userRoles") String role) {
        
        JwtTokenProvider jwtTokenProvider = createProvider();
        
        // Record the time before token generation
        Instant beforeGeneration = Instant.now();
        
        // Generate access token
        String accessToken = jwtTokenProvider.generateAccessToken(userId, email, role);
        
        // Generate refresh token
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        
        // Record the time after token generation
        Instant afterGeneration = Instant.now();
        
        // Verify both tokens are non-null and non-empty
        assertThat(accessToken).isNotNull().isNotEmpty();
        assertThat(refreshToken).isNotNull().isNotEmpty();
        
        // Verify access token expiration is exactly 15 minutes
        Instant accessTokenExpiration = jwtTokenProvider.getExpirationFromToken(accessToken);
        Duration accessTokenDuration = Duration.between(beforeGeneration, accessTokenExpiration);
        
        // Allow 1 second tolerance for test execution time
        assertThat(accessTokenDuration.toMillis())
                .isBetween(ACCESS_TOKEN_EXPIRATION - 1000, ACCESS_TOKEN_EXPIRATION + 1000);
        
        // Verify refresh token expiration is exactly 7 days
        Instant refreshTokenExpiration = jwtTokenProvider.getExpirationFromToken(refreshToken);
        Duration refreshTokenDuration = Duration.between(beforeGeneration, refreshTokenExpiration);
        
        // Allow 1 second tolerance for test execution time
        assertThat(refreshTokenDuration.toMillis())
                .isBetween(REFRESH_TOKEN_EXPIRATION - 1000, REFRESH_TOKEN_EXPIRATION + 1000);
        
        // Verify access token contains correct user information
        assertThat(jwtTokenProvider.getUserIdFromToken(accessToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getEmailFromToken(accessToken)).isEqualTo(email);
        assertThat(jwtTokenProvider.getRoleFromToken(accessToken)).isEqualTo(role);
        
        // Verify refresh token contains correct user ID
        assertThat(jwtTokenProvider.getUserIdFromToken(refreshToken)).isEqualTo(userId);
    }
    
    /**
     * Property 2: Token Refresh Correctness
     * 
     * **Validates: Requirements 1.3**
     * 
     * For any valid refresh token that has not expired, the system SHALL issue 
     * a new access token with valid signature and 15-minute expiration.
     */
    @Property
    @Label("Property 2: Token Refresh Correctness - New access tokens issued from valid refresh tokens")
    void tokenRefreshCorrectnessProperty(
            @ForAll("uuids") UUID userId,
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String email,
            @ForAll("userRoles") String role) {
        
        JwtTokenProvider jwtTokenProvider = createProvider();
        
        // Generate initial refresh token
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        
        // Verify refresh token is valid (not expired, valid signature)
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.isTokenExpired(refreshToken)).isFalse();
        
        // Record time before new access token generation
        Instant beforeNewToken = Instant.now();
        
        // Generate new access token using user ID from refresh token
        UUID userIdFromRefresh = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userIdFromRefresh, email, role);
        
        // Verify new access token has valid signature
        assertThat(jwtTokenProvider.validateToken(newAccessToken)).isTrue();
        
        // Verify new access token has 15-minute expiration
        Instant newAccessTokenExpiration = jwtTokenProvider.getExpirationFromToken(newAccessToken);
        Duration newAccessTokenDuration = Duration.between(beforeNewToken, newAccessTokenExpiration);
        
        assertThat(newAccessTokenDuration.toMillis())
                .isBetween(ACCESS_TOKEN_EXPIRATION - 1000, ACCESS_TOKEN_EXPIRATION + 1000);
        
        // Verify new access token is not expired
        assertThat(jwtTokenProvider.isTokenExpired(newAccessToken)).isFalse();
        
        // Verify user ID is preserved
        assertThat(jwtTokenProvider.getUserIdFromToken(newAccessToken)).isEqualTo(userId);
    }
    
    /**
     * Property 4: JWT Token Validation
     * 
     * **Validates: Requirements 1.6**
     * 
     * For any JWT token, validation SHALL verify both signature correctness 
     * and non-expiration status.
     */
    @Property
    @Label("Property 4: JWT Token Validation - Validates signature and expiration")
    void jwtTokenValidationProperty(
            @ForAll("uuids") UUID userId,
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String email,
            @ForAll("userRoles") String role) {
        
        JwtTokenProvider jwtTokenProvider = createProvider();
        
        // Generate valid access token
        String validToken = jwtTokenProvider.generateAccessToken(userId, email, role);
        
        // Verify valid token passes validation
        assertThat(jwtTokenProvider.validateToken(validToken)).isTrue();
        
        // Verify valid token is not expired
        assertThat(jwtTokenProvider.isTokenExpired(validToken)).isFalse();
        
        // Test invalid signature - tampered token
        String tamperedToken = validToken + "tampered";
        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
        
        // Test malformed token
        assertThat(jwtTokenProvider.validateToken("not.a.valid.jwt")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        
        // Verify validation checks both signature AND expiration
        // Valid signature + not expired = true
        assertThat(jwtTokenProvider.validateToken(validToken) && !jwtTokenProvider.isTokenExpired(validToken))
                .isTrue();
    }
    
    /**
     * Additional validation: Expired tokens should fail validation
     */
    @Property
    @Label("Property 4 Extension: Expired tokens fail validation")
    void expiredTokensFailValidation(@ForAll("uuids") UUID userId) {
        // Create a token provider with very short expiration (1 millisecond)
        JwtTokenProvider shortExpirationProvider = new JwtTokenProvider(
                TEST_SECRET,
                1L,  // 1 millisecond expiration
                1L
        );
        
        String shortLivedToken = shortExpirationProvider.generateAccessToken(userId, "test@example.com", "ADMIN");
        
        // Wait for token to expire
        try {
            Thread.sleep(10); // Wait 10ms to ensure expiration
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Expired token should fail validation
        assertThat(shortExpirationProvider.isTokenExpired(shortLivedToken)).isTrue();
        assertThat(shortExpirationProvider.validateToken(shortLivedToken)).isFalse();
    }
    
    /**
     * Arbitrary provider for user roles
     */
    @Provide
    Arbitrary<String> userRoles() {
        return Arbitraries.of("ADMIN", "MANAGER", "ANALYST", "VIEWER");
    }
    
    /**
     * Arbitrary provider for UUID
     */
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
