package com.socialmedia.instagram.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Instagram profile management
 * Demonstrates role-based access control implementation
 * Requirements 13.1, 13.2, 13.3, 13.4, 13.5, 13.6
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    
    /**
     * List all profiles - all authenticated users can view
     * GET /api/profiles
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> listProfiles() {
        return ResponseEntity.ok("List of profiles (accessible to all roles)");
    }
    
    /**
     * Get profile details - all authenticated users can view
     * GET /api/profiles/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> getProfile(@PathVariable String id) {
        return ResponseEntity.ok("Profile details for " + id + " (accessible to all roles)");
    }
    
    /**
     * Create new profile - requires ADMIN or MANAGER role
     * POST /api/profiles
     * Requirement 13.2: ADMIN can do all operations
     * Requirement 13.3: MANAGER can add profiles
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<String> createProfile() {
        return ResponseEntity.ok("Profile created (ADMIN or MANAGER only)");
    }
    
    /**
     * Update profile - requires ADMIN or MANAGER role
     * PUT /api/profiles/{id}
     * Requirement 13.2: ADMIN can do all operations
     * Requirement 13.3: MANAGER can modify profiles
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<String> updateProfile(@PathVariable String id) {
        return ResponseEntity.ok("Profile " + id + " updated (ADMIN or MANAGER only)");
    }
    
    /**
     * Delete profile - requires ADMIN role only
     * DELETE /api/profiles/{id}
     * Requirement 13.2: ADMIN can do all operations
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteProfile(@PathVariable String id) {
        return ResponseEntity.ok("Profile " + id + " deleted (ADMIN only)");
    }
}
