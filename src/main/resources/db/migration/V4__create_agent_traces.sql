CREATE TABLE agent_traces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    failure_id UUID NOT NULL REFERENCES failures(id) ON DELETE CASCADE,
    step_index INTEGER NOT NULL,
    thought TEXT,
    tool_name VARCHAR(100),
    tool_input TEXT,
    tool_output TEXT,
    llm_provider VARCHAR(50),
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_traces_failure_id ON agent_traces(failure_id);
CREATE INDEX idx_agent_traces_failure_step ON agent_traces(failure_id, step_index);