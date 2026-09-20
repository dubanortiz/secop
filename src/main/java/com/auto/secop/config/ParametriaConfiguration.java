package com.auto.secop.config;

import com.auto.secop.model.parametria.ParametriaProfile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class ParametriaConfiguration {

    @Bean
    ParametriaProfile parametriaProfile(SecopProperties properties, ObjectMapper objectMapper) throws IOException {
        Resource resource = new DefaultResourceLoader().getResource(properties.profilePath());
        if (!resource.exists()) {
            throw new IllegalStateException("Parametria file not found: " + properties.profilePath());
        }
        ObjectMapper parametriaMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = resource.getInputStream()) {
            return parametriaMapper.readValue(input, ParametriaProfile.class);
        }
    }
}
