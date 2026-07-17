package com.deploybrain.tool;

import com.deploybrain.service.GitHubApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class FixValidator {

    private static final double MAX_CHANGE_RATIO = 0.8;

    private final GitHubApiClient gitHubApiClient;

    public FixValidator(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    public record ValidationResult(boolean passed, String reason) {}

    /**
     * Pre-flight validation run before OpenPRTool ever calls GitHub's
     * write APIs. Confirms the fix is not a no-op, confirms the target
     * file genuinely exists, and uses a simple changed-line-ratio
     * heuristic to catch cases where the agent replaces most/all of a
     * file rather than making a targeted fix.
     */
    public ValidationResult validate(String repo, String path, String commitSha,
                                     String originalContent, String fixedContent) {

        if (fixedContent == null || fixedContent.isBlank()) {
            return new ValidationResult(false, "Fixed content is empty - refusing to open a PR with no content");
        }

        if (originalContent != null && originalContent.equals(fixedContent)) {
            return new ValidationResult(false, "Fixed content is identical to original - this would be a no-op change");
        }

        GitHubApiClient.FileContentResult existing = gitHubApiClient.getFileContent(repo, path, commitSha);
        if (existing == null) {
            return new ValidationResult(false,
                    "Target file '" + path + "' does not exist in the repository at commit " + commitSha);
        }

        if (originalContent != null) {
            double changeRatio = computeChangeRatio(originalContent, fixedContent);
            if (changeRatio > MAX_CHANGE_RATIO) {
                return new ValidationResult(false, String.format(
                        "Fix changes %.0f%% of the file, exceeding the %.0f%% safety threshold - "
                                + "this looks like a full file replacement rather than a targeted fix",
                        changeRatio * 100, MAX_CHANGE_RATIO * 100));
            }
        }

        return new ValidationResult(true, "Validation passed");
    }

    private double computeChangeRatio(String original, String fixed) {
        String[] originalLines = original.split("\n", -1);
        String[] fixedLines = fixed.split("\n", -1);

        Set<String> originalSet = new HashSet<>(Arrays.asList(originalLines));
        Set<String> fixedSet = new HashSet<>(Arrays.asList(fixedLines));

        long unchangedLines = originalSet.stream().filter(fixedSet::contains).count();
        int totalOriginalLines = Math.max(originalLines.length, 1);

        return 1.0 - ((double) unchangedLines / totalOriginalLines);
    }
}