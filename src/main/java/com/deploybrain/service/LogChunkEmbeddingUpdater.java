package com.deploybrain.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LogChunkEmbeddingUpdater {

    private final JdbcTemplate jdbcTemplate;

    public LogChunkEmbeddingUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Writes a float[] embedding into log_chunks.embedding via native SQL,
     * since Hibernate does not cleanly map pgvector's native column type
     * through standard JPA entity persistence.
     */
    public void updateEmbedding(UUID logChunkId, float[] embedding) {
        String vectorLiteral = toPgVectorLiteral(embedding);
        jdbcTemplate.update(
                "UPDATE log_chunks SET embedding = ?::vector WHERE id = ?",
                vectorLiteral,
                logChunkId
        );
    }

    private String toPgVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }
}