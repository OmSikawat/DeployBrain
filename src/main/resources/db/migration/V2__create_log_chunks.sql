CREATE TABLE log_chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    build_id UUID NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    total_chunks INTEGER NOT NULL,
    job_name VARCHAR(255),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_log_chunks_build_id ON log_chunks(build_id);
CREATE INDEX idx_log_chunks_build_chunk ON log_chunks(build_id, chunk_index);