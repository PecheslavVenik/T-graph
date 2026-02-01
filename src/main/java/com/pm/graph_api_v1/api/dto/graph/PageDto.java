package com.pm.graph_api_v1.api.dto.graph;

public record PageDto(
        String cursor,
        int limit,
        String endCursor,
        boolean hasNext
) {}
