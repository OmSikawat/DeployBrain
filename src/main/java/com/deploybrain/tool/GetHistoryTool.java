package com.deploybrain.tool;

import com.deploybrain.entity.Failure;
import com.deploybrain.repository.FailureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GetHistoryTool implements AgentTool {

    private final FailureRepository failureRepository;

    public GetHistoryTool(FailureRepository failureRepository) {
        this.failureRepository = failureRepository;
    }

    @Override
    public String getName() {
        return "get_history";
    }

    @Override
    public String getDescription() {
        return "Looks up past failures of the same type in the same repository that were successfully "
                + "resolved before. Use this to check if there's a known, previously working fix for this kind of failure.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "repo", Map.of("type", "string", "description", "Repository full name, e.g. owner/repo"),
                        "failure_type", Map.of("type", "string", "description",
                                "One of: DEPENDENCY_CONFLICT, TEST_REGRESSION, COMPILATION_ERROR, ENV_MISMATCH, OOM, TIMEOUT")
                ),
                "required", List.of("repo", "failure_type")
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws ToolExecutionException {
        String repo = (String) input.get("repo");
        String failureTypeStr = (String) input.get("failure_type");

        if (repo == null || failureTypeStr == null) {
            throw new ToolExecutionException("get_history requires 'repo' and 'failure_type' parameters");
        }

        Failure.FailureType failureType;
        try {
            failureType = Failure.FailureType.valueOf(failureTypeStr);
        } catch (IllegalArgumentException e) {
            throw new ToolExecutionException("Unknown failure_type: " + failureTypeStr);
        }

        List<Failure> pastFixes = failureRepository
                .findTop3ByBuildRepoNameAndFailureTypeAndAgentStatusOrderByCreatedAtDesc(
                        repo, failureType, Failure.AgentStatus.FIX_MERGED
                );

        if (pastFixes.isEmpty()) {
            return "No prior confirmed fixes found for " + failureType + " failures in " + repo
                    + ". This may be the first time this failure type has been successfully resolved in this repository.";
        }

        StringBuilder sb = new StringBuilder("Found " + pastFixes.size() + " prior resolved case(s):\n\n");
        for (Failure past : pastFixes) {
            sb.append("- Diagnosis: ").append(past.getDiagnosis() != null ? past.getDiagnosis() : "N/A").append("\n");
            sb.append("  Root cause: ").append(past.getRootCause() != null ? past.getRootCause() : "N/A").append("\n");
            sb.append("  Merged PR: ").append(past.getPrUrl() != null ? past.getPrUrl() : "N/A").append("\n\n");
        }
        return sb.toString();
    }
}