package com.pm.graph_api_v1.repository;

import java.util.List;

public record ShortestPathRaw(
        List<String> vertexIds,
        List<String> edgeIds,
        long length
) {}
