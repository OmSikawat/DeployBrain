package com.deploybrain.service;

import com.deploybrain.dto.BuildResponse;
import com.deploybrain.dto.StatsResponse;
import com.deploybrain.entity.AgentTrace;
import com.deploybrain.entity.Build;
import com.deploybrain.entity.Failure;
import com.deploybrain.repository.AgentTraceRepository;
import com.deploybrain.repository.BuildRepository;
import com.deploybrain.repository.FailureRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final BuildRepository buildRepository;
    private final FailureRepository failureRepository;
    private final AgentTraceRepository agentTraceRepository;

    public DashboardService(BuildRepository buildRepository, FailureRepository failureRepository,
                            AgentTraceRepository agentTraceRepository) {
        this.buildRepository = buildRepository;
        this.failureRepository = failureRepository;
        this.agentTraceRepository = agentTraceRepository;
    }

    public Page<BuildResponse> getBuilds(Pageable pageable) {
        Page<Build> builds = buildRepository.findAllByOrderByTriggeredAtDesc(pageable);
        return builds.map(this::toResponseWithoutTrace);
    }

    public Optional<BuildResponse> getBuildDetail(UUID buildId) {
        return buildRepository.findById(buildId).map(build -> {
            BuildResponse response = toResponseWithoutTrace(build);
            failureRepository.findByBuildId(buildId).ifPresent(failure -> {
                List<AgentTrace> traces = agentTraceRepository.findByFailureIdOrderByStepIndex(failure.getId());
                response.setTraceSteps(traces.stream().map(this::toTraceStep).collect(Collectors.toList()));
            });
            return response;
        });
    }

    public StatsResponse.FailureTypeStats getFailureTypeStats() {
        List<Failure> all = failureRepository.findAll();
        Map<String, Long> counts = all.stream()
                .filter(f -> f.getFailureType() != null)
                .collect(Collectors.groupingBy(f -> f.getFailureType().name(), Collectors.counting()));
        return new StatsResponse.FailureTypeStats(counts, buildRepository.count());
    }

    /**
     * MTTR = time from build.triggeredAt to build.updatedAt on a build that
     * reached a terminal AGENT_COMPLETE state. build.updatedAt is used as a
     * proxy for "resolution time" since Failure.resolvedAt is only set once
     * Day 25's PRMergeWebhookHandler (stretch goal) confirms a merge - not
     * yet wired in. This is an honest, documented approximation.
     */
    public StatsResponse.MttrStats getMttrStats() {
        List<Build> completed = buildRepository.findAll().stream()
                .filter(b -> b.getStatus() == Build.BuildStatus.AGENT_COMPLETE)
                .collect(Collectors.toList());

        Map<String, List<Double>> byWeek = new TreeMap<>();
        List<Double> allMinutes = new ArrayList<>();

        for (Build b : completed) {
            long minutes = ChronoUnit.MINUTES.between(b.getTriggeredAt(), b.getUpdatedAt());
            String weekLabel = "W" + b.getTriggeredAt().get(WeekFields.ISO.weekOfWeekBasedYear());
            byWeek.computeIfAbsent(weekLabel, k -> new ArrayList<>()).add((double) minutes);
            allMinutes.add((double) minutes);
        }

        List<StatsResponse.MttrStats.WeekPoint> trend = byWeek.entrySet().stream()
                .map(e -> new StatsResponse.MttrStats.WeekPoint(e.getKey(), average(e.getValue())))
                .collect(Collectors.toList());

        return new StatsResponse.MttrStats(trend, average(allMinutes));
    }

    public StatsResponse.FixRateStats getFixRateStats() {
        List<Failure> all = failureRepository.findAll();

        Map<String, Long> fixGeneratedByType = all.stream()
                .filter(f -> f.getFailureType() != null && f.getAgentStatus() == Failure.AgentStatus.FIX_GENERATED)
                .collect(Collectors.groupingBy(f -> f.getFailureType().name(), Collectors.counting()));

        Map<String, Long> totalAttemptedByType = all.stream()
                .filter(f -> f.getFailureType() != null &&
                        (f.getAgentStatus() == Failure.AgentStatus.FIX_GENERATED
                                || f.getAgentStatus() == Failure.AgentStatus.NEEDS_REVIEW
                                || f.getAgentStatus() == Failure.AgentStatus.FIX_MERGED))
                .collect(Collectors.groupingBy(f -> f.getFailureType().name(), Collectors.counting()));

        Map<String, Double> fixRateByType = new HashMap<>();
        for (String type : totalAttemptedByType.keySet()) {
            long fixed = fixGeneratedByType.getOrDefault(type, 0L);
            long total = totalAttemptedByType.get(type);
            fixRateByType.put(type, total == 0 ? 0.0 : (double) fixed / total);
        }

        long totalFixGenerated = fixGeneratedByType.values().stream().mapToLong(Long::longValue).sum();
        long totalNeedsReview = all.stream()
                .filter(f -> f.getAgentStatus() == Failure.AgentStatus.NEEDS_REVIEW).count();
        long totalAttempted = totalAttemptedByType.values().stream().mapToLong(Long::longValue).sum();
        double overallRate = totalAttempted == 0 ? 0.0 : (double) totalFixGenerated / totalAttempted;

        return new StatsResponse.FixRateStats(fixRateByType, totalFixGenerated, totalNeedsReview, overallRate);
    }

    private double average(List<Double> values) {
        return values.isEmpty() ? 0.0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private BuildResponse toResponseWithoutTrace(Build build) {
        BuildResponse.BuildResponseBuilder builder = BuildResponse.builder()
                .id(build.getId())
                .repoName(build.getRepoName())
                .workflowName(build.getWorkflowName())
                .commitSha(build.getCommitSha())
                .status(build.getStatus().name())
                .triggeredAt(build.getTriggeredAt());

        failureRepository.findByBuildId(build.getId()).ifPresent(f -> {
            builder.failureId(f.getId())
                    .failureType(f.getFailureType() != null ? f.getFailureType().name() : null)
                    .confidence(f.getConfidence())
                    .agentStatus(f.getAgentStatus() != null ? f.getAgentStatus().name() : null)
                    .prUrl(f.getPrUrl())
                    .llmProviderUsed(f.getLlmProviderUsed())
                    .diagnosis(f.getDiagnosis())
                    .rootCause(f.getRootCause())
                    .evidenceLines(f.getEvidenceLines());
        });

        return builder.build();
    }

    private BuildResponse.TraceStep toTraceStep(AgentTrace t) {
        return BuildResponse.TraceStep.builder()
                .stepIndex(t.getStepIndex())
                .thought(t.getThought())
                .toolName(t.getToolName())
                .toolInput(t.getToolInput())
                .toolOutput(t.getToolOutput())
                .llmProvider(t.getLlmProvider())
                .durationMs(t.getDurationMs())
                .createdAt(t.getCreatedAt())
                .build();
    }
}