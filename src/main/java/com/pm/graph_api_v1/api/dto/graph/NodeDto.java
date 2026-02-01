package com.pm.graph_api_v1.api.dto.graph;

import java.util.Map;
import java.util.Set;

public record NodeDto(
        String id,          // стабильный id: "person:123", "phone:+7999..."
        String kind,        // тип: person/phone/company/...
        String label,       // короткий текст для UI
        Map<String, Object> attrs, // поля для hover
        Set<String> flags   // статусы: vip/blacklist/...
) {}
