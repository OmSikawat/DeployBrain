package com.deploybrain.tool;

import com.deploybrain.service.GitHubApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GetDiffTool implements AgentTool {

    private static final int MAX_FILES = 10;
    private static final int MAX_PATCH_CHARS = 3000;

    private final GitHubApiClient gitHubApiClient;

    public GetDiffTool(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public String getName() {
        return "get_diff";
    }

    @Override
    public String getDescription() {
        return "Gets the file changes and diff patches for the commit that triggered the build failure. "
                + "Use this to see exactly what code changed and whether it likely caused the failure.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "repo", Map.of("type", "string", "description", "Repository full name, e.g. owner/repo"),
                        "commit_sha", Map.of("type", "string", "description", "Commit SHA to get the diff for")
                ),
                "required", List.of("repo", "commit_sha")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> input) throws ToolExecutionException {
        String repo = (String) input.get("repo");
        String commitSha = (String) input.get("commit_sha");

        if (repo == null || commitSha == null) {
            throw new ToolExecutionException("get_diff requires 'repo' and 'commit_sha' parameters");
        }

        try {
            List<Map<String, Object>> files = gitHubApiClient.getCommitFiles(repo, commitSha);

            if (files.isEmpty()) {
                return "No file changes detected in commit " + commitSha + " (empty diff or commit not found).";
            }

            StringBuilder sb = new StringBuilder();
            int fileCount = Math.min(files.size(), MAX_FILES);

            for (int i = 0; i < fileCount; i++) {
                Map<String, Object> file = files.get(i);
                String filename = (String) file.get("filename");
                String status = (String) file.get("status");
                String patch = (String) file.get("patch");

                sb.append("File: ").append(filename).append(" (").append(status).append(")\n");

                if (patch == null) {
                    sb.append("[no textual diff available - likely a binary file]\n\n");
                } else {
                    if (patch.length() > MAX_PATCH_CHARS) {
                        patch = patch.substring(0, MAX_PATCH_CHARS) + "\n... [diff truncated]";
                    }
                    sb.append(patch).append("\n\n");
                }
            }

            if (files.size() > MAX_FILES) {
                sb.append("... and ").append(files.size() - MAX_FILES).append(" more file(s) not shown.\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("get_diff tool failed for repo={}, sha={}: {}", repo, commitSha, e.getMessage());
            throw new ToolExecutionException("Failed to get diff for commit '" + commitSha + "': " + e.getMessage(), e);
        }
    }
}