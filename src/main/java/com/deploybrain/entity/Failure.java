package com.deploybrain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "failures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Failure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private Build build;

    @Column(name = "failure_type")
    @Enumerated(EnumType.STRING)
    private FailureType failureType;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "evidence_lines", columnDefinition = "TEXT")
    private String evidenceLines;

    @Column(name = "agent_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AgentStatus agentStatus;

    @Column(name = "pr_url", columnDefinition = "TEXT")
    private String prUrl;

    @Column(name = "pr_branch")
    private String prBranch;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "llm_provider_used")
    private String llmProviderUsed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (agentStatus == null) {
            agentStatus = AgentStatus.PENDING;
        }
    }

    public enum FailureType {
        DEPENDENCY_CONFLICT,
        TEST_REGRESSION,
        COMPILATION_ERROR,
        ENV_MISMATCH,
        OOM,
        TIMEOUT
    }

    public enum AgentStatus {
        PENDING,
        RUNNING,
        FIX_GENERATED,
        FIX_MERGED,
        NEEDS_REVIEW,
        ERROR
    }
}