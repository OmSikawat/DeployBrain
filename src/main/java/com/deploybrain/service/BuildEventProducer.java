package com.deploybrain.service;

import com.deploybrain.dto.BuildEventMessage;
import com.deploybrain.entity.Build;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class BuildEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.build-events}")
    private String buildEventsTopic;

    public BuildEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a lightweight "this build is ready for classification" event.
     * Keyed by repoName so all events from the same repository land on the
     * same partition and are processed in order relative to each other.
     */
    public void publishChunkedEvent(Build build) {
        BuildEventMessage event = new BuildEventMessage(
                build.getId(),
                build.getRepoName(),
                LocalDateTime.now()
        );

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(buildEventsTopic, build.getRepoName(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish build-chunked event for build {}: {}",
                                    build.getId(), ex.getMessage());
                        } else {
                            log.info("Published build-chunked event for build {} to partition {}",
                                    build.getId(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize build-chunked event for build {}: {}",
                    build.getId(), e.getMessage());
        }
    }
}