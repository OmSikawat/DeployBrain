CREATE TABLE failures (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    build_id UUID NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    failure_type VARCHAR(50),
    confidence DOUBLE PRECISION,
    evidence_lines TEXT,
    agent_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    pr_url TEXT,
    pr_branch VARCHAR(255),
    diagnosis TEXT,
    root_cause TEXT,
    llm_provider_used VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP
);

CREATE INDEX idx_failures_build_id ON failures(build_id);
CREATE INDEX idx_failures_failure_type ON failures(failure_type);
CREATE INDEX idx_failures_agent_status ON failures(agent_status);