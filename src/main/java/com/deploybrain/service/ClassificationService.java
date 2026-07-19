package com.deploybrain.service;

import com.deploybrain.agent.AgentOrchestrator;
import com.deploybrain.dto.ClassifyRequest;
import com.deploybrain.dto.ClassifyResponse;
import com.deploybrain.entity.Build;
import com.deploybrain.entity.Failure;
import com.deploybrain.entity.LogChunk;
import com.deploybrain.repository.BuildRepository;
import com.deploybrain.repository.FailureRepository;
import com.deploybrain.repository.LogChunkRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class ClassificationService {

    private final RestTemplate mlServiceRestTemplate;
    private final LogChunkRepository logChunkRepository;
    private final BuildRepository buildRepository;
    private final FailureRepository failureRepository;
    private final AgentOrchestrator agentOrchestrator;

    @Value("${ml.service.base-url}")
    private String mlServiceBaseUrl;

    @Value("${ml.service.confidence-threshold}")
    private double confidenceThreshold;

    public ClassificationService(
            @Qualifier("mlServiceRestTemplate") RestTemplate mlServiceRestTemplate,
            LogChunkRepository logChunkRepository,
            BuildRepository buildRepository,
            FailureRepository failureRepository,
            AgentOrchestrator agentOrchestrator
    ) {
        this.mlServiceRestTemplate = mlServiceRestTemplate;
        this.logChunkRepository = logChunkRepository;
        this.buildRepository = buildRepository;
        this.failureRepository = failureRepository;
        this.agentOrchestrator = agentOrchestrator;
    }

    @CircuitBreaker(name = "mlService", fallbackMethod = "classifyFallback")
    @Retry(name = "mlService")
    public void classify(Build build) {
        try {
            List<LogChunk> chunks = logChunkRepository.findByBuildIdOrderByChunkIndex(build.getId());

            if (chunks.isEmpty()) {
                log.warn("No log chunks found for build {} - cannot classify", build.getId());
                build.setStatus(Build.BuildStatus.ERROR);
                buildRepository.save(build);
                return;
            }

            String combinedText = reassembleChunks(chunks);

            if (combinedText.isBlank()) {
                log.warn("Reassembled log text is blank for build {} - cannot classify", build.getId());
                build.setStatus(Build.BuildStatus.ERROR);
                buildRepository.save(build);
                return;
            }

            ClassifyRequest request = new ClassifyRequest(combinedText, build.getId().toString());
            ClassifyResponse response = mlServiceRestTemplate.postForObject(
                    mlServiceBaseUrl + "/classify",
                    request,
                    ClassifyResponse.class
            );

            if (response == null) {
                log.error("ML service returned null response for build {}", build.getId());
                markClassificationFailed(build);
                return;
            }

            Failure.FailureType failureType;
            try {
                failureType = Failure.FailureType.valueOf(response.getFailureType());
            } catch (IllegalArgumentException e) {
                log.error("ML service returned unrecognized failure type '{}' for build {} - treating as classification failure",
                        response.getFailureType(), build.getId());
                markClassificationFailed(build);
                return;
            }

            double confidence = response.getConfidence() != null ? response.getConfidence() : 0.0;
            String evidenceLines = response.getEvidenceLines() != null
                    ? String.join("\n", response.getEvidenceLines())
                    : "";

            Failure failure = Failure.builder()
                    .build(build)
                    .failureType(failureType)
                    .confidence(confidence)
                    .evidenceLines(evidenceLines)
                    .build();

            Build.BuildStatus resultStatus;
            Failure.AgentStatus agentStatus;

            if (confidence >= confidenceThreshold) {
                resultStatus = Build.BuildStatus.AGENT_PENDING;
                agentStatus = Failure.AgentStatus.PENDING;
            } else {
                resultStatus = Build.BuildStatus.NEEDS_REVIEW;
                agentStatus = Failure.AgentStatus.NEEDS_REVIEW;
            }

            failure.setAgentStatus(agentStatus);
            failureRepository.save(failure);

            build.setStatus(resultStatus);
            buildRepository.save(build);

            log.info("Classified build {}: type={}, confidence={}, status={}",
                    build.getId(), failureType, confidence, resultStatus);

            if (resultStatus == Build.BuildStatus.AGENT_PENDING) {
                agentOrchestrator.investigateAndFix(failure);
            }

        } catch (Exception e) {
            log.error("Unexpected error during classification for build {}: {} - {}",
                    build.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            markClassificationFailed(build);
        }
    }

    private void classifyFallback(Build build, Exception ex) {
        log.error("ML service call failed after retry for build {}: {}", build.getId(), ex.getMessage());
        markClassificationFailed(build);
    }

    private void markClassificationFailed(Build build) {
        build.setStatus(Build.BuildStatus.CLASSIFICATION_FAILED);
        buildRepository.save(build);
    }

    private String reassembleChunks(List<LogChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        String currentJob = null;

        for (LogChunk chunk : chunks) {
            if (!chunk.getJobName().equals(currentJob)) {
                if (currentJob != null) sb.append("\n\n--- job boundary ---\n\n");
                currentJob = chunk.getJobName();
            }
            sb.append(chunk.getContent()).append("\n");
        }

        return sb.toString();
    }
}