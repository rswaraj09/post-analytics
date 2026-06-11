package com.socialmedia.instagram.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity representing system users with authentication credentials
 * Passwords are stored using bcrypt hashing with cost factor 12
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "password_hash")
    private String passwordHash;
    
    @Column(name = "full_name", nullable = false)
    private String fullName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * Set password with bcrypt hashing (cost factor 12)
     * @param plainPassword Raw password to hash and store
     */
    public void setPassword(String plainPassword) {
        this.passwordHash = PASSWORD_ENCODER.encode(plainPassword);
    }
    
    /**
     * Verify if provided password matches stored hash
     * @param plainPassword Password to verify
     * @return true if password matches
     */
    public boolean verifyPassword(String plainPassword) {
        return PASSWORD_ENCODER.matches(plainPassword, this.passwordHash);
    }
    
    /**
     * User roles defining permission levels
     */
    public enum UserRole {
        ADMIN,      // Full system access
        MANAGER,    // Can add profiles, posts, create rules, export data
        ANALYST,    // Can view all data, create own rules, export data
        VIEWER      // Read-only access
    }
}
