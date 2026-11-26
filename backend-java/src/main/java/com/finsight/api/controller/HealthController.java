package com.finsight.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@Tag(name = "Health", description = "Liveness probe used by Render")
public class HealthController {

    private final String version;

    public HealthController(@Value("${info.app.version:unknown}") String version) {
        this.version = version;
    }

    @GetMapping("/api/health")
    @Operation(summary = "Service health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "finsight-api", "version", version,
                "timestamp", Instant.now().toString());
    }
}
