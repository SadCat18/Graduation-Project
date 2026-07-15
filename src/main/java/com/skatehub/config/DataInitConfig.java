package com.skatehub.config;

import com.skatehub.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class DataInitConfig {

    @Bean
    public CommandLineRunner initAdmin(AuthService authService,
                                       @Value("${admin.bootstrap.enabled:false}") boolean adminBootstrapEnabled) {
        return args -> {
            if (!adminBootstrapEnabled) {
                log.info("Default admin bootstrap is disabled.");
                return;
            }
            authService.ensureDefaultAdmin();
        };
    }
}
