package com.socialmedia.instagram.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an Instagram post being tracked
 */
@Entity
@Table(name = "instagram_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstagramPost {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private InstagramProfile profile;

    @Column(unique = true, nullable = false, length = 500)
    private String postUrl;

    @Column(length = 50)
    private String postShortcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PostType postType;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(columnDefinition = "TEXT[]")
    private String[] hashtags;

    @Column(columnDefinition = "TEXT[]")
    private String[] mentions;

    @Column(length = 500)
    private String mediaUrl;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant postedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCompetitorPost = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MonitoringStatus monitoringStatus = MonitoringStatus.ACTIVE;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lastFetchedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;
}
