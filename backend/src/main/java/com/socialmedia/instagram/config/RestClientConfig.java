package com.socialmedia.instagram.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for REST clients and JSON serialization
 */
@Configuration
public class RestClientConfig {

    /**
     * RestTemplate bean for HTTP requests
     * Used by GraphApiService to call Instagram Graph API
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .requestFactory(this::clientHttpRequestFactory)
            .setConnectTimeout(Duration.ofSeconds(30))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Configure request factory with timeouts
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30 seconds
        factory.setReadTimeout(30000);    // 30 seconds
        return factory;
    }

    /**
     * ObjectMapper bean for JSON serialization/deserialization
     * Configured to handle Java 8+ date/time types
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
