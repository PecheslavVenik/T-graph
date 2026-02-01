package com.pm.graph_api_v1.repository;

import com.pm.graph_api_v1.api.dto.graph.EdgeDto;
import com.pm.graph_api_v1.api.dto.graph.NodeDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DuckDbGraphRepository {
    List<NodeDto> findNodesByIds(Collection<String> ids);
    List<NodeDto> findNodesByLookup(String lookupKind, Collection<String> values);
    List<EdgeDto> findOutEdges(String src, String cursor, int limit, Set<String> edgeKinds);
    Optional<EdgeDto> findEdge(String src, String dst, Set<String> edgeKinds);
    List<String> shortestPathVertices(String from, String to, int maxHops, Set<String> edgeKinds);
}
