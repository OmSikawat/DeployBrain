package com.deploybrain.repository;

import com.deploybrain.entity.LogChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LogChunkRepository extends JpaRepository<LogChunk, UUID> {

    List<LogChunk> findByBuildIdOrderByChunkIndex(UUID buildId);

    long countByBuildId(UUID buildId);

    @Query(value = """
        SELECT lc.id, lc.build_id, lc.chunk_index, lc.total_chunks,
               lc.job_name, lc.content, lc.created_at,
               1 - (lc.embedding <=> CAST(:embedding AS vector)) AS similarity
        FROM log_chunks lc
        WHERE lc.build_id = :buildId
          AND lc.embedding IS NOT NULL
        ORDER BY lc.embedding <=> CAST(:embedding AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> findSimilarChunks(
            @Param("buildId") UUID buildId,
            @Param("embedding") String embedding,
            @Param("topK") int topK
    );
}