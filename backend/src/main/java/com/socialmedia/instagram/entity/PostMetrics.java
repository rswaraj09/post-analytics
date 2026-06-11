package com.socialmedia.instagram.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing metrics snapshot for an Instagram post
 */
@Entity
@Table(name = "post_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMetrics {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private InstagramPost post;

    private Long likesCount;

    private Long commentsCount;

    private Long viewsCount;

    private Long videoViews;

    private Long reach;

    private Long impressions;

    private Long saves;

    private Long shares;

    private Long profileVisits;

    @Column(precision = 6, scale = 4)
    private BigDecimal engagementRate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isEstimated = false;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant fetchedAt;
}
