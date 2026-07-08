package com.deploybrain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "builds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Build {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "repo_owner", nullable = false)
    private String repoOwner;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(name = "workflow_run_id", nullable = false, unique = true)
    private Long workflowRunId;

    @Column(name = "logs_url", columnDefinition = "TEXT")
    private String logsUrl;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private BuildStatus status;

    @Column(name = "log_size_bytes")
    private Long logSizeBytes;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        triggeredAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = BuildStatus.RECEIVED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BuildStatus {
        RECEIVED,
        LOGS_FETCHED,
        LOGS_CHUNKED,
        CLASSIFYING,
        AGENT_PENDING,
        AGENT_RUNNING,
        AGENT_COMPLETE,
        NEEDS_REVIEW,
        CLASSIFICATION_FAILED,
        ERROR
    }
}