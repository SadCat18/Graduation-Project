package com.javademo1.config;

import com.javademo1.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitConfig {

    @Bean
    public CommandLineRunner initAdmin(AuthService authService) {
        return args -> authService.ensureDefaultAdmin();
    }
}

