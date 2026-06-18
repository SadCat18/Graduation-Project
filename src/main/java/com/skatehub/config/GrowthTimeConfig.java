package com.skatehub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class GrowthTimeConfig {

    @Bean
    public Clock growthClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
