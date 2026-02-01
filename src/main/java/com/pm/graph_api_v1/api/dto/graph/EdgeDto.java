package com.pm.graph_api_v1.api.dto.graph;

import java.util.Map;
import java.util.Set;

public record EdgeDto(
        String id,      // стабильный id ребра
        String src,
        String dst,
        String kind,    // тип связи: transfer/tk/...
        Map<String, Object> attrs,
        Set<String> flags   // статусы/теги ребра
) {}
