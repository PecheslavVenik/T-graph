CREATE TABLE IF NOT EXISTS edges (src BIGINT, dst BIGINT);
CREATE INDEX IF NOT EXISTS idx_edges_src_dst ON edges(src, dst);
