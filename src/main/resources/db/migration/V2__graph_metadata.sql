CREATE TABLE IF NOT EXISTS nodes (
    id VARCHAR PRIMARY KEY,
    kind VARCHAR NOT NULL,
    label VARCHAR,
    attrs JSON,
    flags JSON
);

CREATE TABLE IF NOT EXISTS node_lookup (
    lookup_kind VARCHAR NOT NULL,
    lookup_value VARCHAR NOT NULL,
    node_id VARCHAR NOT NULL,
    PRIMARY KEY (lookup_kind, lookup_value, node_id)
);

CREATE INDEX IF NOT EXISTS idx_node_lookup_kind_value ON node_lookup(lookup_kind, lookup_value);
CREATE INDEX IF NOT EXISTS idx_nodes_kind ON nodes(kind);

DROP TABLE IF EXISTS edges_new;

CREATE TABLE edges_new (
    src VARCHAR,
    dst VARCHAR,
    id VARCHAR,
    kind VARCHAR,
    attrs JSON,
    flags JSON
);

INSERT INTO edges_new (src, dst, id, kind, attrs, flags)
SELECT
    CAST(src AS VARCHAR),
    CAST(dst AS VARCHAR),
    CAST(src AS VARCHAR) || '->' || CAST(dst AS VARCHAR) || ':edge' AS id,
    'edge' AS kind,
    NULL AS attrs,
    NULL AS flags
FROM edges;

DROP TABLE edges;
ALTER TABLE edges_new RENAME TO edges;

CREATE INDEX IF NOT EXISTS idx_edges_src_kind_dst ON edges(src, kind, dst);
