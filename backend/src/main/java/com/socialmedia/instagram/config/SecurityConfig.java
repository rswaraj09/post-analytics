package com.socialmedia.instagram.config;

import com.socialmedia.instagram.security.JwtAuthenticationFilter;
import com.socialmedia.instagram.security.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for JWT and OAuth2 authentication
 * Implements Requirements 1.1, 1.2, 1.7, 13.1-13.6
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless API
                .csrf(csrf -> csrf.disable())
                
                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Stateless session management (JWT-based)
                .sessionManagement(session -> 
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/google", "/api/auth/google/callback").permitAll()
                        .requestMatchers("/ws/**").permitAll() // WebSocket endpoint
                        .requestMatchers("/error").permitAll()
                        
                        // Demo endpoints (remove in production!)
                        .requestMatchers("/api/demo/**").permitAll()
                        
                        // Health check endpoints
                        .requestMatchers("/actuator/health").permitAll()
                        
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                
                // Configure OAuth2 login
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/api/auth/google")
                        .defaultSuccessUrl("/api/auth/google/callback")
                        .successHandler(oAuth2SuccessHandler)
                        .failureUrl("/api/auth/google/failure")
                )
                
                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * CORS configuration bean
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow origins from configuration
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000"
        ));
        
        // Allow common HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        
        // Allow all headers
        configuration.setAllowedHeaders(List.of("*"));
        
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        // Max age for preflight requests
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
