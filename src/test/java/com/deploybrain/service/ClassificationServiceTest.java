package com.deploybrain.service;

import com.deploybrain.dto.ClassifyRequest;
import com.deploybrain.dto.ClassifyResponse;
import com.deploybrain.entity.Build;
import com.deploybrain.entity.Failure;
import com.deploybrain.entity.LogChunk;
import com.deploybrain.repository.BuildRepository;
import com.deploybrain.repository.FailureRepository;
import com.deploybrain.repository.LogChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {

    @Mock
    private RestTemplate mlServiceRestTemplate;

    @Mock
    private LogChunkRepository logChunkRepository;

    @Mock
    private BuildRepository buildRepository;

    @Mock
    private FailureRepository failureRepository;

    @InjectMocks
    private ClassificationService classificationService;

    private Build testBuild;

    @BeforeEach
    void setUp() {
        testBuild = Build.builder()
                .id(UUID.randomUUID())
                .repoName("test/repo")
                .repoOwner("test")
                .commitSha("abc123")
                .workflowRunId(1L)
                .status(Build.BuildStatus.LOGS_CHUNKED)
                .build();

        ReflectionTestUtils.setField(classificationService, "mlServiceBaseUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(classificationService, "confidenceThreshold", 0.75);

        LogChunk chunk = LogChunk.builder()
                .id(UUID.randomUUID())
                .build(testBuild)
                .chunkIndex(0)
                .totalChunks(1)
                .jobName("build")
                .content("some real log content")
                .build();

        when(logChunkRepository.findByBuildIdOrderByChunkIndex(testBuild.getId()))
                .thenReturn(List.of(chunk));
    }

    @Test
    void highConfidenceResponse_shouldRouteToAgentPending() {
        ClassifyResponse response = new ClassifyResponse();
        response.setFailureType("DEPENDENCY_CONFLICT");
        response.setConfidence(0.90);
        response.setEvidenceLines(List.of("Could not resolve dependency"));

        when(mlServiceRestTemplate.postForObject(anyString(), any(ClassifyRequest.class), eq(ClassifyResponse.class)))
                .thenReturn(response);

        classificationService.classify(testBuild);

        assertEquals(Build.BuildStatus.AGENT_PENDING, testBuild.getStatus());
        verify(failureRepository).save(argThat(f ->
                f.getFailureType() == Failure.FailureType.DEPENDENCY_CONFLICT
                        && f.getAgentStatus() == Failure.AgentStatus.PENDING
        ));
    }

    @Test
    void confidenceExactlyAtThreshold_shouldRouteToAgentPending() {
        // boundary test: threshold check is >= 0.75, so exactly 0.75
        // must route to AGENT_PENDING, not NEEDS_REVIEW
        ClassifyResponse response = new ClassifyResponse();
        response.setFailureType("TIMEOUT");
        response.setConfidence(0.75);
        response.setEvidenceLines(List.of("timed out"));

        when(mlServiceRestTemplate.postForObject(anyString(), any(ClassifyRequest.class), eq(ClassifyResponse.class)))
                .thenReturn(response);

        classificationService.classify(testBuild);

        assertEquals(Build.BuildStatus.AGENT_PENDING, testBuild.getStatus());
    }

    @Test
    void confidenceJustBelowThreshold_shouldRouteToNeedsReview() {
        ClassifyResponse response = new ClassifyResponse();
        response.setFailureType("COMPILATION_ERROR");
        response.setConfidence(0.74);
        response.setEvidenceLines(List.of("cannot find symbol"));

        when(mlServiceRestTemplate.postForObject(anyString(), any(ClassifyRequest.class), eq(ClassifyResponse.class)))
                .thenReturn(response);

        classificationService.classify(testBuild);

        assertEquals(Build.BuildStatus.NEEDS_REVIEW, testBuild.getStatus());
        verify(failureRepository).save(argThat(f ->
                f.getAgentStatus() == Failure.AgentStatus.NEEDS_REVIEW
        ));
    }

    @Test
    void nullMlServiceResponse_shouldMarkClassificationFailed() {
        when(mlServiceRestTemplate.postForObject(anyString(), any(ClassifyRequest.class), eq(ClassifyResponse.class)))
                .thenReturn(null);

        classificationService.classify(testBuild);

        assertEquals(Build.BuildStatus.CLASSIFICATION_FAILED, testBuild.getStatus());
    }

    @Test
    void unrecognizedFailureType_shouldMarkClassificationFailed() {
        ClassifyResponse response = new ClassifyResponse();
        response.setFailureType("SOME_TYPE_THE_MODEL_NEVER_TRAINED_ON");
        response.setConfidence(0.90);

        when(mlServiceRestTemplate.postForObject(anyString(), any(ClassifyRequest.class), eq(ClassifyResponse.class)))
                .thenReturn(response);

        classificationService.classify(testBuild);

        assertEquals(Build.BuildStatus.CLASSIFICATION_FAILED, testBuild.getStatus());
    }

    @Test
    void emptyLogChunks_shouldMarkBuildAsError() {
        when(logChunkRepository.findByBuildIdOrderByChunkIndex(testBuild.getId()))
                .thenReturn(List.of());

        classificationService.classify(testBuild);

        assertEquals(Build.BuildStatus.ERROR, testBuild.getStatus());
        verifyNoInteractions(mlServiceRestTemplate);
    }

    @Test
    void unexpectedExceptionDuringClassification_shouldMarkClassificationFailedNotCrash() {
        // simulates today's real-world PSQLException scenario - an
        // unexpected exception from the repository layer must be caught
        // and result in a clean CLASSIFICATION_FAILED status, not an
        // unhandled exception propagating up and crashing the Kafka
        // consumer thread.
        when(logChunkRepository.findByBuildIdOrderByChunkIndex(testBuild.getId()))
                .thenThrow(new RuntimeException("Simulated PSQLException: No results were returned by the query"));

        assertDoesNotThrow(() -> classificationService.classify(testBuild));

        assertEquals(Build.BuildStatus.CLASSIFICATION_FAILED, testBuild.getStatus());
    }
}