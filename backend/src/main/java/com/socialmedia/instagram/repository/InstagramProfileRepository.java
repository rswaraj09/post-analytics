package com.socialmedia.instagram.repository;

import com.socialmedia.instagram.entity.InstagramProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for InstagramProfile entity
 */
@Repository
public interface InstagramProfileRepository extends JpaRepository<InstagramProfile, UUID> {

    /**
     * Find profile by username
     */
    Optional<InstagramProfile> findByUsername(String username);

    /**
     * Check if username already exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if profile URL already exists
     */
    boolean existsByProfileUrl(String profileUrl);
}
