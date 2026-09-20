package com.auto.secop.config;

import com.auto.secop.model.parametria.ParametriaProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class ParametriaConfiguration {

    @Bean
    ParametriaProfile parametriaProfile(SecopProperties properties) throws IOException {
        Resource resource = new DefaultResourceLoader().getResource(properties.profilePath());
        if (!resource.exists()) {
            throw new IllegalStateException("Parametria file not found: " + properties.profilePath());
        }
        ObjectMapper parametriaMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream input = resource.getInputStream()) {
            return parametriaMapper.readValue(input, ParametriaProfile.class);
        }
    }
}
