package com.socialmedia.instagram.controller;

import com.socialmedia.instagram.entity.User;
import com.socialmedia.instagram.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for authentication endpoints
 * Implements Requirements 1.1, 1.2, 1.3, 1.4
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    /**
     * Login endpoint
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthService.AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResponse response = authService.login(request.email(), request.password());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Registration endpoint
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthService.AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthResponse response = authService.register(
                request.email(),
                request.password(),
                request.fullName(),
                User.UserRole.valueOf(request.role())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Refresh token endpoint
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthService.RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.RefreshResponse response = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Logout endpoint
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Google OAuth2 initiate endpoint (handled by Spring Security)
     * GET /api/auth/google
     */
    @GetMapping("/google")
    public void googleLogin() {
        // This is handled by Spring Security OAuth2 client
        // Redirects to Google for authentication
    }
    
    /**
     * Google OAuth2 callback endpoint (handled by OAuth2SuccessHandler)
     * GET /api/auth/google/callback
     */
    @GetMapping("/google/callback")
    public void googleCallback() {
        // This is handled by OAuth2SuccessHandler
    }
    
    /**
     * Google OAuth2 failure endpoint
     * GET /api/auth/google/failure
     */
    @GetMapping("/google/failure")
    public ResponseEntity<ErrorResponse> googleFailure() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("OAuth2 authentication failed"));
    }
    
    /**
     * Exception handler for authentication errors
     */
    @ExceptionHandler(AuthService.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthService.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ex.getMessage()));
    }
    
    // Request/Response DTOs
    
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password
    ) {}
    
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank String fullName,
            @NotBlank String role
    ) {}
    
    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}
    
    public record LogoutRequest(
            @NotBlank String refreshToken
    ) {}
    
    public record ErrorResponse(String message) {}
}
