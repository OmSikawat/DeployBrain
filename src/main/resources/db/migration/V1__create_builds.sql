CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE builds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repo_name VARCHAR(255) NOT NULL,
    repo_owner VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(40) NOT NULL,
    workflow_name VARCHAR(255),
    workflow_run_id BIGINT NOT NULL UNIQUE,
    logs_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    log_size_bytes BIGINT,
    triggered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_builds_repo_name ON builds(repo_name);
CREATE INDEX idx_builds_status ON builds(status);
CREATE INDEX idx_builds_triggered_at ON builds(triggered_at DESC);
CREATE INDEX idx_builds_workflow_run_id ON builds(workflow_run_id);