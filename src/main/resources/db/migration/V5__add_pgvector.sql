CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE log_chunks ADD COLUMN embedding vector(768);

CREATE INDEX idx_log_chunks_embedding
    ON log_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);