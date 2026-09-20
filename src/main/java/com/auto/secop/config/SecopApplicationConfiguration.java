package com.auto.secop.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SecopProperties.class)
public class SecopApplicationConfiguration {
}
