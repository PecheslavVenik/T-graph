package com.pm.graph_api_v1.api.dto.graph;

import java.util.List;

public record PathResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        int length
) {}
