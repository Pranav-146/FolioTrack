package com.foliotrack.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Lets the React dev server (a different origin: port 5173) call the API from the browser.
// Only that one origin is allowed, and only the methods the dashboard uses; every other origin's
// preflight gets a 403. Server-to-server callers (curl, Postman) don't send Origin and are unaffected.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigin;

    public CorsConfig(@Value("${foliotrack.cors.allowed-origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type");
    }
}
