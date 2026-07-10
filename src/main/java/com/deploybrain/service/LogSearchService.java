package com.deploybrain.service;

import com.deploybrain.dto.SimilarChunkResult;
import com.deploybrain.repository.LogChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class LogSearchService {

    private static final int DEFAULT_TOP_K = 3;

    private final EmbeddingService embeddingService;
    private final LogChunkRepository logChunkRepository;

    public LogSearchService(EmbeddingService embeddingService, LogChunkRepository logChunkRepository) {
        this.embeddingService = embeddingService;
        this.logChunkRepository = logChunkRepository;
    }

    public List<SimilarChunkResult> search(UUID buildId, String query) {
        return search(buildId, query, DEFAULT_TOP_K);
    }

    /**
     * Semantically searches a build's chunks using pgvector cosine
     * similarity rather than keyword matching. Returns empty list (not an
     * exception) if the query itself fails to embed.
     */
    public List<SimilarChunkResult> search(UUID buildId, String query, int topK) {
        float[] queryVector = embeddingService.embed(query);

        if (queryVector == null) {
            log.warn("Could not embed query '{}' - returning no results", query);
            return List.of();
        }

        String vectorLiteral = toPgVectorLiteral(queryVector);
        List<Object[]> rows = logChunkRepository.findSimilarChunks(buildId, vectorLiteral, topK);

        List<SimilarChunkResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            String jobName = (String) row[4];
            String content = (String) row[5];
            double similarity = ((Number) row[7]).doubleValue();
            results.add(new SimilarChunkResult(content, jobName, similarity));
        }
        return results;
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