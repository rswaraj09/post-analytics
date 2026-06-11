package com.socialmedia.instagram.security;

import com.socialmedia.instagram.entity.User;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for role-based access control
 * Use with @PreAuthorize to specify required roles
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    User.UserRole[] value();
}
