package com.socialmedia.instagram.service;

import com.socialmedia.instagram.entity.RefreshToken;
import com.socialmedia.instagram.entity.User;
import com.socialmedia.instagram.repository.RefreshTokenRepository;
import com.socialmedia.instagram.repository.UserRepository;
import com.socialmedia.instagram.security.JwtTokenProvider;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AuthService
 */
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-jwt-token-generation-min-256-bits-required",
        "jwt.access-token-expiration=900000",
        "jwt.refresh-token-expiration=604800000"
})
@Transactional
class AuthServicePropertyTest {
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    /**
     * Property 60: Logout Token Deletion
     * 
     * **Validates: Requirements 17.4**
     * 
     * When a user logs out, the refresh token SHALL be deleted from the database
     * to prevent reuse.
     */
    @Property(tries = 50)
    @Label("Property 60: Logout Token Deletion - Refresh tokens deleted on logout")
    void logoutTokenDeletionProperty(
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String emailPrefix,
            @ForAll @StringLength(min = 8, max = 20) String password,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String fullName) {
        
        // Create unique email to avoid conflicts
        String email = emailPrefix + "@test.com";
        
        // Register a new user
        AuthService.AuthResponse registerResponse = authService.register(
                email,
                password,
                fullName,
                User.UserRole.VIEWER
        );
        
        String refreshToken = registerResponse.getRefreshToken();
        UUID userId = registerResponse.getUserId();
        
        // Verify refresh token exists in database
        Optional<RefreshToken> tokenBeforeLogout = refreshTokenRepository.findByToken(refreshToken);
        assertThat(tokenBeforeLogout).isPresent();
        assertThat(tokenBeforeLogout.get().getUser().getId()).isEqualTo(userId);
        
        // Perform logout
        authService.logout(refreshToken);
        
        // Verify refresh token has been deleted from database
        Optional<RefreshToken> tokenAfterLogout = refreshTokenRepository.findByToken(refreshToken);
        assertThat(tokenAfterLogout).isEmpty();
        
        // Verify user still exists (only token should be deleted)
        Optional<User> userAfterLogout = userRepository.findById(userId);
        assertThat(userAfterLogout).isPresent();
        assertThat(userAfterLogout.get().getEmail()).isEqualTo(email);
    }
    
    /**
     * Extension: Verify logout with invalid token doesn't cause errors
     */
    @Property(tries = 20)
    @Label("Property 60 Extension: Logout with non-existent token is safe")
    void logoutWithNonExistentTokenIsSafe(@ForAll @StringLength(min = 50, max = 200) String nonExistentToken) {
        // This should not throw an exception
        authService.logout(nonExistentToken);
        
        // Verify no token exists with this value
        Optional<RefreshToken> token = refreshTokenRepository.findByToken(nonExistentToken);
        assertThat(token).isEmpty();
    }
    
    /**
     * Extension: Verify logoutAllSessions deletes all tokens for a user
     */
    @Property(tries = 30)
    @Label("Property 60 Extension: Logout all sessions deletes all user tokens")
    void logoutAllSessionsDeletesAllTokens(
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String emailPrefix,
            @ForAll @StringLength(min = 8, max = 20) String password,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String fullName) {
        
        String email = emailPrefix + "@test.com";
        
        // Register user and get first token
        AuthService.AuthResponse response1 = authService.register(
                email,
                password,
                fullName,
                User.UserRole.VIEWER
        );
        
        UUID userId = response1.getUserId();
        String refreshToken1 = response1.getRefreshToken();
        
        // Login again to create a second token
        AuthService.AuthResponse response2 = authService.login(email, password);
        String refreshToken2 = response2.getRefreshToken();
        
        // Verify both tokens exist
        assertThat(refreshTokenRepository.findByToken(refreshToken1)).isPresent();
        assertThat(refreshTokenRepository.findByToken(refreshToken2)).isPresent();
        
        // Logout all sessions
        authService.logoutAllSessions(userId);
        
        // Verify both tokens are deleted
        assertThat(refreshTokenRepository.findByToken(refreshToken1)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(refreshToken2)).isEmpty();
        
        // Verify user still exists
        assertThat(userRepository.findById(userId)).isPresent();
    }
    
    /**
     * Extension: Verify expired tokens can be cleaned up
     */
    @Property(tries = 20)
    @Label("Property 60 Extension: Expired tokens cleanup works correctly")
    void expiredTokensCleanup(
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String emailPrefix,
            @ForAll @StringLength(min = 8, max = 20) String password,
            @ForAll @StringLength(min = 3, max = 30) @AlphaChars String fullName) {
        
        String email = emailPrefix + "@test.com";
        
        // Register user
        AuthService.AuthResponse response = authService.register(
                email,
                password,
                fullName,
                User.UserRole.VIEWER
        );
        
        String refreshToken = response.getRefreshToken();
        
        // Verify token exists
        assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();
        
        // Run cleanup (token should not be expired yet)
        int deletedCount = authService.cleanupExpiredTokens();
        
        // Token should still exist (not expired)
        assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();
    }
}
