package com.deploybrain.tool;

import com.deploybrain.service.GitHubApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ReadFileTool implements AgentTool {

    private static final int MAX_FILE_SIZE_CHARS = 50_000;

    private final GitHubApiClient gitHubApiClient;

    public ReadFileTool(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "Reads the content of a specific file from the GitHub repository at a given commit. "
                + "Use this to inspect source files that may be related to the build failure.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "repo", Map.of("type", "string", "description", "Repository full name, e.g. owner/repo"),
                        "path", Map.of("type", "string", "description", "File path within the repository"),
                        "commit_sha", Map.of("type", "string", "description", "Commit SHA or branch name to read the file at")
                ),
                "required", List.of("repo", "path", "commit_sha")
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws ToolExecutionException {
        String repo = (String) input.get("repo");
        String path = (String) input.get("path");
        String commitSha = (String) input.get("commit_sha");

        if (repo == null || path == null || commitSha == null) {
            throw new ToolExecutionException("read_file requires 'repo', 'path', and 'commit_sha' parameters");
        }

        try {
            GitHubApiClient.FileContentResult result = gitHubApiClient.getFileContent(repo, path, commitSha);

            if (result == null) {
                return "File not found: '" + path + "' does not exist at commit " + commitSha
                        + ". It may have been added in this commit, renamed, or the path may be incorrect.";
            }

            String content = result.content();
            if (content.length() > MAX_FILE_SIZE_CHARS) {
                content = content.substring(0, MAX_FILE_SIZE_CHARS)
                        + "\n... [truncated, file exceeds " + MAX_FILE_SIZE_CHARS + " characters]";
            }
            return content;

        } catch (Exception e) {
            log.error("read_file tool failed for repo={}, path={}, sha={}: {}", repo, path, commitSha, e.getMessage());
            throw new ToolExecutionException("Failed to read file '" + path + "': " + e.getMessage(), e);
        }
    }
}