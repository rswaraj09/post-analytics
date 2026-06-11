package com.socialmedia.instagram.repository;

import com.socialmedia.instagram.entity.InstagramPost;
import com.socialmedia.instagram.entity.MonitoringStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for InstagramPost entity
 */
@Repository
public interface InstagramPostRepository extends JpaRepository<InstagramPost, UUID> {

    /**
     * Find post by post URL
     */
    Optional<InstagramPost> findByPostUrl(String postUrl);

    /**
     * Find all posts by profile ID
     */
    Page<InstagramPost> findByProfileId(UUID profileId, Pageable pageable);

    /**
     * Find all posts by monitoring status
     */
    List<InstagramPost> findByMonitoringStatus(MonitoringStatus status);

    /**
     * Find all active posts that need metrics update
     * (either never fetched or last fetched before the given time)
     */
    @Query("SELECT p FROM InstagramPost p WHERE p.monitoringStatus = 'ACTIVE' " +
           "AND (p.lastFetchedAt IS NULL OR p.lastFetchedAt < :cutoffTime)")
    List<InstagramPost> findActivePostsNeedingUpdate(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Count posts by profile ID
     */
    long countByProfileId(UUID profileId);

    /**
     * Check if post URL already exists
     */
    boolean existsByPostUrl(String postUrl);
}
