package com.socialmedia.instagram.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Provider for generating and validating JWT tokens
 * Implements access token (15-minute expiration) and refresh token (7-day expiration) generation
 */
@Component
public class JwtTokenProvider {
    
    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    
    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration; // 15 minutes in milliseconds
        this.refreshTokenExpiration = refreshTokenExpiration; // 7 days in milliseconds
    }
    
    /**
     * Generate JWT access token with 15-minute expiration
     * @param userId User's UUID
     * @param email User's email
     * @param role User's role
     * @return JWT access token
     */
    public String generateAccessToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(accessTokenExpiration);
        
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("type", "access")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiryDate))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Generate refresh token with 7-day expiration
     * @param userId User's UUID
     * @return JWT refresh token
     */
    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(refreshTokenExpiration);
        
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("type", "refresh")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiryDate))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Validate JWT token signature and expiration
     * @param token JWT token to validate
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }
    
    /**
     * Extract user ID from JWT token
     * @param token JWT token
     * @return User UUID
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return UUID.fromString(claims.getSubject());
    }
    
    /**
     * Extract email from JWT token
     * @param token JWT token
     * @return User email
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.get("email", String.class);
    }
    
    /**
     * Extract role from JWT token
     * @param token JWT token
     * @return User role
     */
    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.get("role", String.class);
    }
    
    /**
     * Check if token is expired
     * @param token JWT token
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }
    
    /**
     * Get expiration date from token
     * @param token JWT token
     * @return Expiration instant
     */
    public Instant getExpirationFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.getExpiration().toInstant();
    }
}
