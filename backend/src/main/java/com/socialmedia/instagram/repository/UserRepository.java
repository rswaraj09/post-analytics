package com.socialmedia.instagram.repository;

import com.socialmedia.instagram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Find user by email address
     * @param email User's email
     * @return Optional containing user if found
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if user exists with given email
     * @param email Email to check
     * @return true if user exists
     */
    boolean existsByEmail(String email);
}
