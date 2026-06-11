package com.socialmedia.instagram.repository;

import com.socialmedia.instagram.entity.RefreshToken;
import com.socialmedia.instagram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken entity operations
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    /**
     * Find refresh token by token string
     * @param token Token string
     * @return Optional containing refresh token if found
     */
    Optional<RefreshToken> findByToken(String token);
    
    /**
     * Delete all refresh tokens for a user (logout)
     * @param user User entity
     */
    void deleteByUser(User user);
    
    /**
     * Delete specific refresh token by token string
     * @param token Token string to delete
     */
    void deleteByToken(String token);
    
    /**
     * Delete all expired refresh tokens (cleanup)
     * @param now Current timestamp
     * @return Number of deleted tokens
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(Instant now);
}
