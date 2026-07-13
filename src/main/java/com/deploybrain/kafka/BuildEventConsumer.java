package com.deploybrain.kafka;

import com.deploybrain.dto.BuildEventMessage;
import com.deploybrain.entity.Build;
import com.deploybrain.repository.BuildRepository;
import com.deploybrain.service.BuildStateService;
import com.deploybrain.service.ClassificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class BuildEventConsumer {

    private final ObjectMapper objectMapper;
    private final BuildStateService buildStateService;
    private final BuildRepository buildRepository;
    private final ClassificationService classificationService;

    public BuildEventConsumer(
            ObjectMapper objectMapper,
            BuildStateService buildStateService,
            BuildRepository buildRepository,
            ClassificationService classificationService
    ) {
        this.objectMapper = objectMapper;
        this.buildStateService = buildStateService;
        this.buildRepository = buildRepository;
        this.classificationService = classificationService;
    }

    @KafkaListener(
            topics = "${kafka.topic.build-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeBuildEvent(String rawMessage) {

        BuildEventMessage event;
        try {
            event = objectMapper.readValue(rawMessage, BuildEventMessage.class);
        } catch (Exception e) {
            log.error("Failed to deserialize build event, skipping malformed message: {} - raw: {}",
                    e.getMessage(), rawMessage);
            return;
        }

        if (!buildStateService.tryMarkProcessing(event.getBuildId())) {
            log.info("Build {} already being processed or already processed - skipping duplicate event",
                    event.getBuildId());
            return;
        }

        Optional<Build> buildOpt = buildRepository.findById(event.getBuildId());
        if (buildOpt.isEmpty()) {
            log.warn("Received event for unknown build {} - skipping", event.getBuildId());
            return;
        }

        Build build = buildOpt.get();
        classificationService.classify(build);

        buildStateService.markProcessed(event.getBuildId());
    }
}