package com.deploybrain.kafka;

import com.deploybrain.dto.BuildEventMessage;
import com.deploybrain.entity.LogChunk;
import com.deploybrain.repository.LogChunkRepository;
import com.deploybrain.service.EmbeddingService;
import com.deploybrain.service.LogChunkEmbeddingUpdater;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class LogChunkEmbeddingListener {

    private final ObjectMapper objectMapper;
    private final LogChunkRepository logChunkRepository;
    private final EmbeddingService embeddingService;
    private final LogChunkEmbeddingUpdater embeddingUpdater;

    public LogChunkEmbeddingListener(
            ObjectMapper objectMapper,
            LogChunkRepository logChunkRepository,
            EmbeddingService embeddingService,
            LogChunkEmbeddingUpdater embeddingUpdater
    ) {
        this.objectMapper = objectMapper;
        this.logChunkRepository = logChunkRepository;
        this.embeddingService = embeddingService;
        this.embeddingUpdater = embeddingUpdater;
    }

    /**
     * Separate consumer group ("embedding-processors") from
     * BuildEventConsumer's ("log-processors") so both independently receive
     * every event on build-events rather than competing within one group.
     */
    @KafkaListener(
            topics = "${kafka.topic.build-events}",
            groupId = "${kafka.consumer.embedding-group-id}"
    )
    public void consumeForEmbedding(String rawMessage) {

        BuildEventMessage event;
        try {
            event = objectMapper.readValue(rawMessage, BuildEventMessage.class);
        } catch (Exception e) {
            log.error("Embedding listener: failed to deserialize event, skipping: {}", e.getMessage());
            return;
        }

        List<LogChunk> chunks = logChunkRepository.findByBuildIdOrderByChunkIndex(event.getBuildId());

        if (chunks.isEmpty()) {
            log.warn("No chunks found for build {} - nothing to embed", event.getBuildId());
            return;
        }

        int embedded = 0;
        int skipped = 0;

        // Partial-success policy: embed what succeeds, skip and log what
        // fails, never abort the whole batch over one bad chunk.
        for (LogChunk chunk : chunks) {
            try {
                float[] vector = embeddingService.embed(chunk.getContent());
                if (vector == null) {
                    skipped++;
                    continue;
                }
                embeddingUpdater.updateEmbedding(chunk.getId(), vector);
                embedded++;
            } catch (Exception e) {
                log.error("Unexpected error embedding chunk {} for build {}: {}",
                        chunk.getId(), event.getBuildId(), e.getMessage());
                skipped++;
            }
        }

        log.info("Embedding complete for build {}: {} embedded, {} skipped out of {} total chunks",
                event.getBuildId(), embedded, skipped, chunks.size());
    }
}