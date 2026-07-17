package com.deploybrain.tool;

import com.deploybrain.service.GitHubApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class OpenPRTool implements AgentTool {

    private final GitHubApiClient gitHubApiClient;
    private final FixValidator fixValidator;

    public OpenPRTool(GitHubApiClient gitHubApiClient, FixValidator fixValidator) {
        this.gitHubApiClient = gitHubApiClient;
        this.fixValidator = fixValidator;
    }

    @Override
    public String getName() {
        return "open_pr";
    }

    @Override
    public String getDescription() {
        return "Creates a new branch, commits a proposed fix to a file, and opens a pull request on GitHub "
                + "with a structured description of the diagnosis and fix. Only call this once confident in the "
                + "root cause and the specific fix to apply.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("repo", Map.of("type", "string", "description", "Repository full name, e.g. owner/repo")),
                        Map.entry("base_branch", Map.of("type", "string", "description", "Branch to base the fix on")),
                        Map.entry("commit_sha", Map.of("type", "string", "description", "The failing commit's SHA")),
                        Map.entry("file_path", Map.of("type", "string", "description", "Path of the file to fix")),
                        Map.entry("fixed_content", Map.of("type", "string", "description", "Complete new content for the file")),
                        Map.entry("failure_type", Map.of("type", "string", "description", "The classified failure type")),
                        Map.entry("confidence", Map.of("type", "number", "description", "Classification confidence, 0 to 1")),
                        Map.entry("root_cause", Map.of("type", "string", "description", "Explanation of the root cause")),
                        Map.entry("evidence_lines", Map.of("type", "string", "description", "Relevant log lines")),
                        Map.entry("fix_description", Map.of("type", "string", "description", "What was changed and why"))
                ),
                "required", List.of("repo", "base_branch", "commit_sha", "file_path", "fixed_content",
                        "failure_type", "confidence", "fix_description")
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws ToolExecutionException {
        String repo = (String) input.get("repo");
        String baseBranch = (String) input.get("base_branch");
        String commitSha = (String) input.get("commit_sha");
        String filePath = (String) input.get("file_path");
        String fixedContent = (String) input.get("fixed_content");
        String failureType = (String) input.get("failure_type");
        Double confidence = input.get("confidence") != null ? ((Number) input.get("confidence")).doubleValue() : 0.0;
        String rootCause = (String) input.get("root_cause");
        String evidenceLines = (String) input.get("evidence_lines");
        String fixDescription = (String) input.get("fix_description");

        if (repo == null || baseBranch == null || commitSha == null || filePath == null
                || fixedContent == null || failureType == null || fixDescription == null) {
            throw new ToolExecutionException(
                    "open_pr requires 'repo', 'base_branch', 'commit_sha', 'file_path', 'fixed_content', "
                            + "'failure_type', and 'fix_description'");
        }

        try {
            GitHubApiClient.FileContentResult existing = gitHubApiClient.getFileContent(repo, filePath, commitSha);
            String originalContent = existing != null ? existing.content() : null;

            FixValidator.ValidationResult validation = fixValidator.validate(
                    repo, filePath, commitSha, originalContent, fixedContent);

            if (!validation.passed()) {
                log.warn("open_pr validation failed for repo={}, path={}: {}", repo, filePath, validation.reason());
                return "PR NOT created - validation failed: " + validation.reason();
            }

            String branchName = "deploybrain/fix-" + UUID.randomUUID().toString().substring(0, 8);
            if (gitHubApiClient.branchExists(repo, branchName)) {
                branchName = branchName + "-" + System.currentTimeMillis();
            }

            gitHubApiClient.createBranch(repo, branchName, baseBranch);

            GitHubApiClient.FileContentResult fileOnNewBranch = gitHubApiClient.getFileContent(repo, filePath, branchName);
            String fileSha = fileOnNewBranch != null ? fileOnNewBranch.sha() : null;

            String commitMessage = "DeployBrain: fix " + failureType + " in " + filePath;
            gitHubApiClient.updateFile(repo, filePath, branchName, fixedContent, fileSha, commitMessage);

            String prTitle = PullRequestBody.buildTitle(failureType, repo);
            String prBody = PullRequestBody.build(failureType, confidence, rootCause, evidenceLines, fixDescription, filePath);

            String prUrl = gitHubApiClient.createPullRequest(repo, prTitle, prBody, branchName, baseBranch);

            log.info("Opened PR for repo={}, failureType={}: {}", repo, failureType, prUrl);
            return "Pull request opened successfully: " + prUrl;

        } catch (Exception e) {
            log.error("open_pr tool failed for repo={}, path={}: {}", repo, filePath, e.getMessage());
            throw new ToolExecutionException("Failed to open PR: " + e.getMessage(), e);
        }
    }
}