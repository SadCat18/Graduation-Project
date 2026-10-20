package com.skatehub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@org.springframework.validation.annotation.Validated
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled;
    // Empty by default: the socket peer is authoritative until deployment verifies its proxy CIDRs.
    private java.util.List<String> trustedProxies = java.util.List.of();
    @jakarta.validation.constraints.Min(1)
    private int boardIpPerMinute = 30;

    public void setTrustedProxies(java.util.List<String> values) {
        values.forEach(com.skatehub.util.ClientIpResolver::validateTrustedProxy);
        trustedProxies = java.util.List.copyOf(values);
    }
    private boolean redisFailureDeniesProtected = true;
    private int loginIpPerMinute = 10;
    private int loginAccountPerFiveMinutes = 10;
    private int registerIpPerHour = 20;
    private int aiUserPerMinute = 10;
    private int uploadUserPerMinute = 5;
    private int writeUserPerMinute = 10;
}
