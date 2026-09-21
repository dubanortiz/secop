package com.auto.secop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfiguration {

    @Bean
    Clock americaBogotaClock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
