package com.pm.graph_api_v1.api.dto.graph;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record ShortestPathRequest(
        @NotBlank @Size(max = 200) String from,
        @NotBlank @Size(max = 200) String to,

        @Size(max = 20)
        Set<String> edgeKinds,

        // чтобы не улететь в бесконечные обходы в MVP
        int maxHops
) {}
