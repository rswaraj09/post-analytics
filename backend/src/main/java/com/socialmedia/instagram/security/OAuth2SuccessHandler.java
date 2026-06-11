package com.socialmedia.instagram.security;

import com.socialmedia.instagram.entity.RefreshToken;
import com.socialmedia.instagram.entity.User;
import com.socialmedia.instagram.repository.RefreshTokenRepository;
import com.socialmedia.instagram.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;

/**
 * OAuth2 Success Handler for Google login callback
 * Creates or retrieves user by email and issues JWT tokens
 * Requirement 1.2: Google OAuth2 integration
 */
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);
    
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final String frontendUrl;
    
    public OAuth2SuccessHandler(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            @Value("${cors.allowed-origins}") String allowedOrigins) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        // Extract first allowed origin as frontend URL
        this.frontendUrl = allowedOrigins.split(",")[0];
    }
    
    /**
     * Handle successful OAuth2 authentication
     * Creates or finds user by email, generates tokens, and redirects to frontend
     */
    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            log.error("Unexpected authentication type: {}", authentication.getClass().getName());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid authentication");
            return;
        }
        
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        
        // Extract user information from Google OAuth2 response
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        
        if (email == null || email.isBlank()) {
            log.error("Email not provided by OAuth2 provider");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not provided");
            return;
        }
        
        log.info("OAuth2 login attempt for email: {}", email);
        
        try {
            // Find or create user by email
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> createUserFromOAuth(email, name));
            
            // Check if user is active
            if (!user.getIsActive()) {
                log.warn("OAuth2 login attempt for inactive user: {}", email);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Account is inactive");
                return;
            }
            
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
            
            log.info("OAuth2 login successful for user: {}", user.getId());
            
            // Redirect to frontend with tokens as URL parameters
            String redirectUrl = String.format(
                    "%s/auth/callback?accessToken=%s&refreshToken=%s",
                    frontendUrl,
                    accessToken,
                    refreshToken
            );
            
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
            
        } catch (Exception e) {
            log.error("Error during OAuth2 authentication", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed");
        }
    }
    
    /**
     * Create new user from OAuth2 provider data
     * Default role is VIEWER for new OAuth users
     */
    private User createUserFromOAuth(String email, String name) {
        log.info("Creating new user from OAuth2: {}", email);
        
        User user = User.builder()
                .email(email)
                .fullName(name != null ? name : email)
                .role(User.UserRole.VIEWER) // Default role for OAuth users
                .isActive(true)
                .passwordHash(null) // OAuth users don't have passwords
                .build();
        
        return userRepository.save(user);
    }
}
