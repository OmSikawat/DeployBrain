package com.deploybrain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildResponse {
    private UUID id;
    private String repoName;
    private String workflowName;
    private String commitSha;
    private String status;
    private LocalDateTime triggeredAt;

    // populated only when a Failure row exists for this build
    private UUID failureId;
    private String failureType;
    private Double confidence;
    private String agentStatus;
    private String prUrl;
    private String llmProviderUsed;
    private String diagnosis;
    private String rootCause;
    private String evidenceLines;

    // populated only on the detail endpoint, null in list view
    private List<TraceStep> traceSteps;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TraceStep {
        private Integer stepIndex;
        private String thought;
        private String toolName;
        private String toolInput;
        private String toolOutput;
        private String llmProvider;
        private Long durationMs;
        private LocalDateTime createdAt;
    }
}