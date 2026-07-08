package com.deploybrain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_traces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "failure_id", nullable = false)
    private Failure failure;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    @Column(name = "thought", columnDefinition = "TEXT")
    private String thought;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "tool_input", columnDefinition = "TEXT")
    private String toolInput;

    @Column(name = "tool_output", columnDefinition = "TEXT")
    private String toolOutput;

    @Column(name = "llm_provider")
    private String llmProvider;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}