package com.pm.graph_api_v1.api.dto.graph;

import java.util.List;

public record GraphResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        List<SeedPageDto> pages
) {}
