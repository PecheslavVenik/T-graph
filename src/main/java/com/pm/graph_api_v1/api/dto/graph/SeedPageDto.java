package com.pm.graph_api_v1.api.dto.graph;

public record SeedPageDto(
        String seed,
        String endCursor,
        boolean hasNext
) {}
