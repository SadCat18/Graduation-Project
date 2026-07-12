package com.skatehub.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkateHubHealthIndicatorTest {

    @Test
    void exposesBusinessReadinessDetails() {
        SkateHubHealthIndicator indicator = new SkateHubHealthIndicator();

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("module", "skatehub-core");
        assertThat(health.getDetails()).containsEntry("p1Observability", "enabled");
        Map<?, ?> dependencies = (Map<?, ?>) health.getDetails().get("dependencies");
        assertTrue(dependencies.containsKey("database"));
        assertTrue(dependencies.containsKey("redis"));
        assertTrue(dependencies.containsKey("fileStorage"));
        assertTrue(dependencies.containsKey("aiProviderConfig"));
    }
}
