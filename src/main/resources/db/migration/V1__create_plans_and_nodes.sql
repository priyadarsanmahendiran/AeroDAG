CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Plans ──────────────────────────────────────────────────────────────────

CREATE TABLE plans (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    global_objective TEXT         NOT NULL,
    status           VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_plans PRIMARY KEY (id)
);

-- ── Nodes ──────────────────────────────────────────────────────────────────

CREATE TABLE nodes (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    plan_id         UUID         NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    instruction     TEXT         NOT NULL,
    dependencies    JSONB        NOT NULL DEFAULT '[]',
    tools_allowed   JSONB        NOT NULL DEFAULT '[]',
    result_payload  TEXT,

    CONSTRAINT pk_nodes          PRIMARY KEY (id),
    CONSTRAINT fk_nodes_plan     FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE
);

CREATE INDEX idx_nodes_plan_id ON nodes (plan_id);
