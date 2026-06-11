package com.socialmedia.instagram.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * RefreshToken entity for managing JWT refresh tokens
 * Enables token refresh without requiring re-authentication
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, unique = true, length = 500)
    private String token;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * Check if this refresh token has expired
     * @return true if token is expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
