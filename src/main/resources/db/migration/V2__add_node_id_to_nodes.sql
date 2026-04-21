ALTER TABLE nodes ADD COLUMN node_id VARCHAR(100);

CREATE INDEX idx_nodes_node_id ON nodes (node_id);
