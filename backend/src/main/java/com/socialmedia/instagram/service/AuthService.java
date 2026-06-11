package com.socialmedia.instagram.service;

import com.socialmedia.instagram.entity.RefreshToken;
import com.socialmedia.instagram.entity.User;
import com.socialmedia.instagram.repository.RefreshTokenRepository;
import com.socialmedia.instagram.repository.UserRepository;
import com.socialmedia.instagram.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication Service handling user login, registration, and token management
 * Implements Requirements 1.1, 1.4, 17.1, 17.4
 */
@Service
@Transactional
public class AuthService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    
    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    /**
     * Authenticate user with email and password
     * 
     * @param email User's email address
     * @param password Plain text password
     * @return AuthResponse containing tokens and user information
     * @throws AuthenticationException if credentials are invalid
     */
    public AuthResponse login(String email, String password) {
        log.info("Login attempt for email: {}", email);
        
        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));
        
        // Verify user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is inactive");
        }
        
        // Verify password using bcrypt
        if (!user.verifyPassword(password)) {
            throw new AuthenticationException("Invalid email or password");
        }
        
        // Generate JWT tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
        
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        
        // Store refresh token in database (Requirement 17.1)
        Instant expiresAt = jwtTokenProvider.getExpirationFromToken(refreshToken);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(expiresAt)
                .build();
        
        refreshTokenRepository.save(refreshTokenEntity);
        
        log.info("Login successful for user: {}", user.getId());
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
    
    /**
     * Register a new user
     * 
     * @param email User's email address
     * @param password Plain text password
     * @param fullName User's full name
     * @param role User's role
     * @return AuthResponse containing tokens and user information
     * @throws AuthenticationException if email already exists
     */
    public AuthResponse register(String email, String password, String fullName, User.UserRole role) {
        log.info("Registration attempt for email: {}", email);
        
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new AuthenticationException("Email already registered");
        }
        
        // Create new user
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .role(role)
                .isActive(true)
                .build();
        
        // Set password (will be hashed with bcrypt)
        user.setPassword(password);
        
        // Save user
        user = userRepository.save(user);
        
        // Generate JWT tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
        
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        
        // Store refresh token in database
        Instant expiresAt = jwtTokenProvider.getExpirationFromToken(refreshToken);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(expiresAt)
                .build();
        
        refreshTokenRepository.save(refreshTokenEntity);
        
        log.info("Registration successful for user: {}", user.getId());
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
    
    /**
     * Refresh access token using a valid refresh token
     * 
     * @param refreshTokenString Refresh token
     * @return New access token
     * @throws AuthenticationException if refresh token is invalid or expired
     */
    public RefreshResponse refreshAccessToken(String refreshTokenString) {
        log.info("Token refresh attempt");
        
        // Validate refresh token signature
        if (!jwtTokenProvider.validateToken(refreshTokenString)) {
            throw new AuthenticationException("Invalid refresh token");
        }
        
        // Find refresh token in database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new AuthenticationException("Refresh token not found"));
        
        // Check if token is expired
        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new AuthenticationException("Refresh token expired");
        }
        
        // Get user
        User user = refreshToken.getUser();
        
        // Verify user is still active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is inactive");
        }
        
        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
        
        log.info("Token refresh successful for user: {}", user.getId());
        
        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .build();
    }
    
    /**
     * Logout user by invalidating refresh token
     * Requirement 17.4: Delete refresh token from database on logout
     * 
     * @param refreshTokenString Refresh token to invalidate
     */
    public void logout(String refreshTokenString) {
        log.info("Logout attempt");
        
        // Find and delete refresh token
        Optional<RefreshToken> refreshToken = refreshTokenRepository.findByToken(refreshTokenString);
        
        if (refreshToken.isPresent()) {
            refreshTokenRepository.delete(refreshToken.get());
            log.info("Logout successful for user: {}", refreshToken.get().getUser().getId());
        } else {
            log.warn("Logout attempted with non-existent token");
        }
    }
    
    /**
     * Logout user by user ID (invalidate all refresh tokens)
     * 
     * @param userId User's UUID
     */
    public void logoutAllSessions(UUID userId) {
        log.info("Logout all sessions for user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));
        
        refreshTokenRepository.deleteByUser(user);
        
        log.info("All sessions logged out for user: {}", userId);
    }
    
    /**
     * Clean up expired refresh tokens
     * Should be called periodically by a scheduled task
     * 
     * @return Number of expired tokens deleted
     */
    public int cleanupExpiredTokens() {
        log.info("Cleaning up expired refresh tokens");
        int deletedCount = refreshTokenRepository.deleteExpiredTokens(Instant.now());
        log.info("Deleted {} expired refresh tokens", deletedCount);
        return deletedCount;
    }
    
    /**
     * Custom authentication exception
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
    
    /**
     * Response object for login and registration
     */
    public static class AuthResponse {
        private final String accessToken;
        private final String refreshToken;
        private final UUID userId;
        private final String email;
        private final String fullName;
        private final String role;
        
        private AuthResponse(Builder builder) {
            this.accessToken = builder.accessToken;
            this.refreshToken = builder.refreshToken;
            this.userId = builder.userId;
            this.email = builder.email;
            this.fullName = builder.fullName;
            this.role = builder.role;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
        
        public UUID getUserId() {
            return userId;
        }
        
        public String getEmail() {
            return email;
        }
        
        public String getFullName() {
            return fullName;
        }
        
        public String getRole() {
            return role;
        }
        
        public static class Builder {
            private String accessToken;
            private String refreshToken;
            private UUID userId;
            private String email;
            private String fullName;
            private String role;
            
            public Builder accessToken(String accessToken) {
                this.accessToken = accessToken;
                return this;
            }
            
            public Builder refreshToken(String refreshToken) {
                this.refreshToken = refreshToken;
                return this;
            }
            
            public Builder userId(UUID userId) {
                this.userId = userId;
                return this;
            }
            
            public Builder email(String email) {
                this.email = email;
                return this;
            }
            
            public Builder fullName(String fullName) {
                this.fullName = fullName;
                return this;
            }
            
            public Builder role(String role) {
                this.role = role;
                return this;
            }
            
            public AuthResponse build() {
                return new AuthResponse(this);
            }
        }
    }
    
    /**
     * Response object for token refresh
     */
    public static class RefreshResponse {
        private final String accessToken;
        
        private RefreshResponse(Builder builder) {
            this.accessToken = builder.accessToken;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public static class Builder {
            private String accessToken;
            
            public Builder accessToken(String accessToken) {
                this.accessToken = accessToken;
                return this;
            }
            
            public RefreshResponse build() {
                return new RefreshResponse(this);
            }
        }
    }
}
