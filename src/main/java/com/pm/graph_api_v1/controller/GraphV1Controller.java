package com.pm.graph_api_v1.controller;

import com.pm.graph_api_v1.api.dto.graph.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.pm.graph_api_v1.service.GraphExploreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/graph")
@Tag(name = "Graph", description = "Resolve identifiers, 1-hop expansion, shortest path.")
public class GraphV1Controller {

    private final GraphExploreService graphExploreService;

    public GraphV1Controller(GraphExploreService graphExploreService) {
        this.graphExploreService = graphExploreService;
    }

    @PostMapping("/one-hop")
    @Operation(
            summary = "1-hop expansion",
            description = "Expands outgoing edges for seeds or identifiers (ids/phoneNos/partyRks; also phone_no/party_rk)."
    )
    public GraphResponse oneHop(@Valid @RequestBody OneHopRequest req) {
        return graphExploreService.oneHop(req);
    }

    @PostMapping("/resolve")
    @Operation(
            summary = "Resolve identifiers",
            description = "Resolves ids/phoneNos/partyRks to stable node ids."
    )
    public ResolveResponse resolve(@Valid @RequestBody ResolveRequest req) {
        return graphExploreService.resolve(req);
    }

    @PostMapping("/shortest-path")
    @Operation(
            summary = "Shortest path",
            description = "Finds directed shortest path between two node ids with optional edgeKinds and maxHops."
    )
    public PathResponse shortestPath(@Valid @RequestBody ShortestPathRequest req) {
        return graphExploreService.shortestPath(req);
    }

}
