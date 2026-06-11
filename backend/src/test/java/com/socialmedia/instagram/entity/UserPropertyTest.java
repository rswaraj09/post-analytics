package com.socialmedia.instagram.entity;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for User entity password hashing
 */
class UserPropertyTest {
    
    private String getPasswordHashFromUser(User user) {
        try {
            Field field = User.class.getDeclaredField("passwordHash");
            field.setAccessible(true);
            return (String) field.get(user);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access passwordHash", e);
        }
    }
    
    /**
     * Property 3: Password Hashing Security
     * 
     * **Validates: Requirements 1.5**
     * 
     * For any user password, the stored hash SHALL be a valid bcrypt hash with cost factor 12.
     */
    @Property
    @Label("Property 3: Password Hashing Security - Passwords hashed with bcrypt cost factor 12")
    void passwordHashingSecurityProperty(@ForAll @StringLength(min = 8, max = 100) String password) {
        // Create user and set password
        User user = new User();
        user.setPassword(password);
        
        String passwordHash = getPasswordHashFromUser(user);
        
        // Verify password hash is not null
        assertThat(passwordHash).isNotNull().isNotEmpty();
        
        // Verify password hash is not the plain password
        assertThat(passwordHash).isNotEqualTo(password);
        
        // Verify the hash is a valid bcrypt hash (starts with $2a$, $2b$, or $2y$)
        assertThat(passwordHash).matches("^\\$2[ayb]\\$\\d{2}\\$.+$");
        
        // Verify cost factor is 12 (bcrypt hash format: $2a$12$...)
        assertThat(passwordHash).startsWith("$2a$12$");
        
        // Verify the stored hash can be used to verify the original password
        assertThat(user.verifyPassword(password)).isTrue();
        
        // Verify that a different password does not match
        assertThat(user.verifyPassword(password + "wrong")).isFalse();
        
        // Verify bcrypt encoder with cost 12 can validate the hash
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        assertThat(encoder.matches(password, passwordHash)).isTrue();
    }
    
    /**
     * Verify that identical passwords produce different hashes (salt verification)
     */
    @Property
    @Label("Property 3 Extension: Identical passwords produce different hashes due to salt")
    void identicalPasswordsDifferentHashes(@ForAll @StringLength(min = 8, max = 100) String password) {
        User user1 = new User();
        User user2 = new User();
        
        user1.setPassword(password);
        user2.setPassword(password);
        
        String hash1 = getPasswordHashFromUser(user1);
        String hash2 = getPasswordHashFromUser(user2);
        
        // Same password should produce different hashes due to different salts
        assertThat(hash1).isNotEqualTo(hash2);
        
        // But both should verify the same password
        assertThat(user1.verifyPassword(password)).isTrue();
        assertThat(user2.verifyPassword(password)).isTrue();
    }
}
