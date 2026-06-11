package com.socialmedia.instagram.repository;

import com.socialmedia.instagram.entity.PostMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PostMetrics entity
 */
@Repository
public interface PostMetricsRepository extends JpaRepository<PostMetrics, UUID> {

    /**
     * Find latest metrics for a post
     */
    @Query("SELECT pm FROM PostMetrics pm WHERE pm.post.id = :postId " +
           "ORDER BY pm.fetchedAt DESC LIMIT 1")
    Optional<PostMetrics> findLatestByPostId(@Param("postId") UUID postId);

    /**
     * Find latest metrics for a post since a given time
     */
    @Query("SELECT pm FROM PostMetrics pm WHERE pm.post.id = :postId " +
           "AND pm.fetchedAt >= :since ORDER BY pm.fetchedAt DESC LIMIT 1")
    Optional<PostMetrics> findLatestByPostIdSince(@Param("postId") UUID postId, 
                                                    @Param("since") Instant since);

    /**
     * Find metrics history for a post within a date range
     */
    @Query("SELECT pm FROM PostMetrics pm WHERE pm.post.id = :postId " +
           "AND pm.fetchedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY pm.fetchedAt ASC")
    List<PostMetrics> findMetricsHistory(@Param("postId") UUID postId,
                                          @Param("startDate") Instant startDate,
                                          @Param("endDate") Instant endDate);

    /**
     * Delete all metrics for a post
     */
    void deleteByPostId(UUID postId);
}
