package com.pm.graph_api_v1.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Service health checks.")
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns OK when DB is reachable.")
    public String health() {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        return (one != null && one == 1) ? "OK" : "NOT_OK";
    }
}
