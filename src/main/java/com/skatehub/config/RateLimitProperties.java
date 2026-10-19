package com.skatehub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled;
    private boolean redisFailureDeniesProtected = true;
    private int loginIpPerMinute = 10;
    private int loginAccountPerFiveMinutes = 10;
    private int registerIpPerHour = 20;
    private int aiUserPerMinute = 10;
    private int uploadUserPerMinute = 5;
    private int writeUserPerMinute = 10;
}
