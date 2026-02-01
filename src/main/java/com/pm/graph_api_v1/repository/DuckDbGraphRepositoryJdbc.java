package com.pm.graph_api_v1.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pm.graph_api_v1.api.dto.graph.EdgeDto;
import com.pm.graph_api_v1.api.dto.graph.NodeDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class DuckDbGraphRepositoryJdbc implements DuckDbGraphRepository {

    private static final TypeReference<Map<String, Object>> ATTRS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> FLAGS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DuckDbGraphRepositoryJdbc(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NodeDto> findNodesByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT id, kind, label, attrs, flags FROM nodes WHERE id IN (" + placeholders(ids.size()) + ")";
        return jdbc.query(sql, nodeRowMapper(), ids.toArray());
    }

    @Override
    public List<NodeDto> findNodesByLookup(String lookupKind, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        String sql = """
            SELECT DISTINCT n.id, n.kind, n.label, n.attrs, n.flags
            FROM node_lookup l
            JOIN nodes n ON n.id = l.node_id
            WHERE l.lookup_kind = ? AND l.lookup_value IN (""" + placeholders(values.size()) + ")";
        List<Object> args = new ArrayList<>();
        args.add(lookupKind);
        args.addAll(values);
        return jdbc.query(sql, nodeRowMapper(), args.toArray());
    }

    @Override
    public List<EdgeDto> findOutEdges(String src, String cursor, int limit, Set<String> edgeKinds) {
        List<Object> args = new ArrayList<>();
        args.add(src);
        args.add(cursor);

        StringBuilder sql = new StringBuilder("""
            SELECT id, src, dst, kind, attrs, flags
            FROM edges
            WHERE src = ? AND dst > ?
            """);

        appendKindFilter(sql, args, edgeKinds);

        sql.append(" ORDER BY dst LIMIT ?");
        args.add(limit);

        return jdbc.query(sql.toString(), edgeRowMapper(), args.toArray());
    }

    @Override
    public Optional<EdgeDto> findEdge(String src, String dst, Set<String> edgeKinds) {
        List<Object> args = new ArrayList<>();
        args.add(src);
        args.add(dst);

        StringBuilder sql = new StringBuilder("""
            SELECT id, src, dst, kind, attrs, flags
            FROM edges
            WHERE src = ? AND dst = ?
            """);

        appendKindFilter(sql, args, edgeKinds);
        sql.append(" ORDER BY kind LIMIT 1");

        List<EdgeDto> edges = jdbc.query(sql.toString(), edgeRowMapper(), args.toArray());
        if (edges.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(edges.get(0));
    }

    @Override
    public List<String> shortestPathVertices(String from, String to, int maxHops, Set<String> edgeKinds) {
        int hops = Math.max(1, Math.min(maxHops, 20));

        String baseFilter = "";
        String recursiveFilter = "";
        List<Object> args = new ArrayList<>();
        args.add(to);
        args.add(from);

        if (edgeKinds != null && !edgeKinds.isEmpty()) {
            baseFilter = " AND kind IN (" + placeholders(edgeKinds.size()) + ")";
            recursiveFilter = " AND e.kind IN (" + placeholders(edgeKinds.size()) + ")";
        }

        if (edgeKinds != null && !edgeKinds.isEmpty()) {
            args.addAll(edgeKinds);
        }
        args.add(to);
        args.add(hops + 1);
        if (edgeKinds != null && !edgeKinds.isEmpty()) {
            args.addAll(edgeKinds);
        }
        args.add(to);

        String sql = """
            WITH RECURSIVE paths(startNode, endNode, path, endReached) AS (
              SELECT
                src AS startNode,
                dst AS endNode,
                [src, dst] AS path,
                (dst = ?) AS endReached
              FROM edges
              WHERE src = ?""" + baseFilter + """

              UNION ALL

              SELECT
                paths.startNode AS startNode,
                e.dst AS endNode,
                array_append(paths.path, e.dst) AS path,
                max(CASE WHEN e.dst = ? THEN 1 ELSE 0 END)
                  OVER (ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS endReached
              FROM paths
              JOIN edges e ON paths.endNode = e.src
              WHERE
                NOT EXISTS (
                  FROM paths previous_paths
                  WHERE list_contains(previous_paths.path, e.dst)
                )
                AND paths.endReached = 0
                AND length(paths.path) <= ?""" + recursiveFilter + """
            )
            SELECT path
            FROM paths
            WHERE endNode = ?
            ORDER BY length(path), path
            LIMIT 1
            """;

        return jdbc.query(sql, ps -> {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
        }, rs -> {
            if (!rs.next()) return List.<String>of();
            Array arr = rs.getArray("path");
            if (arr == null) {
                return List.of();
            }
            Object raw = arr.getArray();
            if (raw == null) {
                return List.of();
            }
            if (raw instanceof String[] strings) {
                return Arrays.asList(strings);
            }
            if (raw instanceof Object[] objects) {
                return Arrays.stream(objects)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toList();
            }
            return List.of(raw.toString());
        });
    }

    private void appendKindFilter(StringBuilder sql, List<Object> args, Set<String> edgeKinds) {
        if (edgeKinds == null || edgeKinds.isEmpty()) {
            return;
        }
        sql.append(" AND kind IN (").append(placeholders(edgeKinds.size())).append(")");
        args.addAll(edgeKinds);
    }

    private RowMapper<NodeDto> nodeRowMapper() {
        return (rs, rowNum) -> mapNode(rs);
    }

    private RowMapper<EdgeDto> edgeRowMapper() {
        return (rs, rowNum) -> mapEdge(rs);
    }

    private NodeDto mapNode(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String kind = rs.getString("kind");
        String label = rs.getString("label");
        Map<String, Object> attrs = parseAttrs(rs.getString("attrs"));
        Set<String> flags = parseFlags(rs.getString("flags"));

        if (kind == null || kind.isBlank()) {
            kind = detectKind(id);
        }
        if (label == null || label.isBlank()) {
            label = id;
        }

        return new NodeDto(id, kind, label, attrs, flags);
    }

    private EdgeDto mapEdge(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String src = rs.getString("src");
        String dst = rs.getString("dst");
        String kind = rs.getString("kind");
        Map<String, Object> attrs = parseAttrs(rs.getString("attrs"));
        Set<String> flags = parseFlags(rs.getString("flags"));

        if (kind == null || kind.isBlank()) {
            kind = "edge";
        }
        if (id == null || id.isBlank()) {
            id = src + "->" + dst + ":" + kind;
        }

        return new EdgeDto(id, src, dst, kind, attrs, flags);
    }

    private Map<String, Object> parseAttrs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, ATTRS_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse attrs JSON: " + json, e);
        }
    }

    private Set<String> parseFlags(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> flags = objectMapper.readValue(json, FLAGS_TYPE);
            return new LinkedHashSet<>(flags);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse flags JSON: " + json, e);
        }
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static String detectKind(String id) {
        if (id == null) {
            return "entity";
        }
        int idx = id.indexOf(':');
        if (idx > 0) {
            return id.substring(0, idx);
        }
        return "entity";
    }
}
