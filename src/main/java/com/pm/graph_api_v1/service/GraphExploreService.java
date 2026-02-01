package com.pm.graph_api_v1.service;

import com.pm.graph_api_v1.api.dto.graph.*;
import com.pm.graph_api_v1.repository.DuckDbGraphRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraphExploreService {

    private final DuckDbGraphRepository repo;

    public GraphExploreService(DuckDbGraphRepository repo) {
        this.repo = repo;
    }

    public GraphResponse oneHop(OneHopRequest req) {
        int limit = clampLimit(req.limit(), 200);
        String cursor = normalizeCursor(req.cursor());
        Set<String> edgeKinds = normalizeEdgeKinds(req.edgeKinds());

        Set<String> resolvedSeeds = new LinkedHashSet<>(normalizeList(req.seeds()));
        resolvedSeeds.addAll(resolveByIds(req.ids()));
        resolvedSeeds.addAll(resolveByLookup("phone_no", req.phoneNos()));
        resolvedSeeds.addAll(resolveByLookup("party_rk", req.partyRks()));

        if (resolvedSeeds.isEmpty()) {
            return new GraphResponse(List.of(), List.of(), List.of());
        }

        List<String> seeds = new ArrayList<>(resolvedSeeds);
        Map<String, EdgeDto> edgesById = new LinkedHashMap<>();
        List<SeedPageDto> pages = new ArrayList<>(seeds.size());
        Set<String> nodeIds = new LinkedHashSet<>(seeds);

        for (String seed : seeds) {
            List<EdgeDto> edges = repo.findOutEdges(seed, cursor, limit + 1, edgeKinds);

            boolean hasNext = edges.size() > limit;
            List<EdgeDto> pageEdges = edges.subList(0, Math.min(limit, edges.size()));
            String endCursor = pageEdges.isEmpty() ? cursor : pageEdges.get(pageEdges.size() - 1).dst();

            pages.add(new SeedPageDto(seed, endCursor, hasNext));

            for (EdgeDto edge : pageEdges) {
                edgesById.putIfAbsent(edge.id(), edge);
                nodeIds.add(edge.src());
                nodeIds.add(edge.dst());
            }
        }

        Map<String, NodeDto> fetchedNodes = repo.findNodesByIds(nodeIds).stream()
                .collect(Collectors.toMap(NodeDto::id, n -> n, (a, b) -> a, LinkedHashMap::new));

        List<NodeDto> nodes = new ArrayList<>(nodeIds.size());
        for (String nodeId : nodeIds) {
            nodes.add(fetchedNodes.getOrDefault(nodeId, fallbackNode(nodeId)));
        }

        return new GraphResponse(nodes, new ArrayList<>(edgesById.values()), pages);
    }

    public ResolveResponse resolve(ResolveRequest req) {
        Map<String, NodeDto> resolved = new LinkedHashMap<>();

        Set<String> ids = normalizeSet(req.ids());
        if (!ids.isEmpty()) {
            for (NodeDto node : repo.findNodesByIds(ids)) {
                resolved.putIfAbsent(node.id(), node);
            }
            for (NodeDto node : repo.findNodesByLookup("id", ids)) {
                resolved.putIfAbsent(node.id(), node);
            }
        }

        Set<String> phoneNos = normalizeSet(req.phoneNos());
        if (!phoneNos.isEmpty()) {
            for (NodeDto node : repo.findNodesByLookup("phone_no", phoneNos)) {
                resolved.putIfAbsent(node.id(), node);
            }
        }

        Set<String> partyRks = normalizeSet(req.partyRks());
        if (!partyRks.isEmpty()) {
            for (NodeDto node : repo.findNodesByLookup("party_rk", partyRks)) {
                resolved.putIfAbsent(node.id(), node);
            }
        }

        return new ResolveResponse(new ArrayList<>(resolved.values()));
    }

    public PathResponse shortestPath(ShortestPathRequest req) {
        String from = normalizeId(req.from());
        String to = normalizeId(req.to());
        int maxHops = clampMaxHops(req.maxHops());
        Set<String> edgeKinds = normalizeEdgeKinds(req.edgeKinds());

        List<String> path = repo.shortestPathVertices(from, to, maxHops, edgeKinds);
        if (path.isEmpty()) {
            return new PathResponse(List.of(), List.of(), 0);
        }

        Set<String> nodeIds = new LinkedHashSet<>(path);
        Map<String, NodeDto> fetchedNodes = repo.findNodesByIds(nodeIds).stream()
                .collect(Collectors.toMap(NodeDto::id, n -> n, (a, b) -> a, LinkedHashMap::new));

        List<NodeDto> nodes = new ArrayList<>(nodeIds.size());
        for (String nodeId : nodeIds) {
            nodes.add(fetchedNodes.getOrDefault(nodeId, fallbackNode(nodeId)));
        }

        List<EdgeDto> edges = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            String src = path.get(i);
            String dst = path.get(i + 1);
            edges.add(repo.findEdge(src, dst, edgeKinds)
                    .orElseGet(() -> fallbackEdge(src, dst)));
        }

        return new PathResponse(nodes, edges, path.size() - 1);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clampLimit(int limit, int max) {
        if (limit <= 0) return 1;
        return Math.min(limit, max);
    }

    private static int clampMaxHops(int maxHops) {
        if (maxHops <= 0) return 10;
        return Math.min(maxHops, 20);
    }

    private static String normalizeCursor(String cursor) {
        return cursor == null ? "" : cursor.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> normalizeEdgeKinds(Set<String> edgeKinds) {
        if (edgeKinds == null || edgeKinds.isEmpty()) {
            return Set.of();
        }
        return edgeKinds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> resolveByIds(List<String> ids) {
        Set<String> normalized = normalizeSet(ids);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (NodeDto node : repo.findNodesByIds(normalized)) {
            resolved.add(node.id());
        }
        for (NodeDto node : repo.findNodesByLookup("id", normalized)) {
            resolved.add(node.id());
        }
        return resolved;
    }

    private Set<String> resolveByLookup(String lookupKind, List<String> values) {
        Set<String> normalized = normalizeSet(values);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return repo.findNodesByLookup(lookupKind, normalized).stream()
                .map(NodeDto::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static NodeDto fallbackNode(String id) {
        String kind = detectKind(id);
        return new NodeDto(id, kind, id, Map.of(), Set.of());
    }

    private static EdgeDto fallbackEdge(String src, String dst) {
        String kind = "path";
        String edgeId = src + "->" + dst + ":" + kind;
        return new EdgeDto(edgeId, src, dst, kind, Map.of(), Set.of());
    }

    private static String detectKind(String id) {
        int idx = id.indexOf(':');
        if (idx > 0) {
            return id.substring(0, idx);
        }
        return "entity";
    }
}
