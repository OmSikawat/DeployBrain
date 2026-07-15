package com.deploybrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GitHubApiClient {

    private static final String API_BASE = "https://api.github.com";

    private final RestTemplate githubRestTemplate;

    public GitHubApiClient(@Qualifier("githubRestTemplate") RestTemplate githubRestTemplate) {
        this.githubRestTemplate = githubRestTemplate;
    }

    public byte[] downloadLogZip(String logsUrl) {
        ResponseEntity<byte[]> response = githubRestTemplate.exchange(
                logsUrl, HttpMethod.GET, null, byte[].class
        );
        checkRateLimit(response);
        if (response.getBody() == null || response.getBody().length == 0) {
            throw new IllegalStateException("GitHub returned an empty log archive for URL: " + logsUrl);
        }
        return response.getBody();
    }

    /**
     * Fetches a file's content at a specific commit via GitHub's Contents
     * API. Returns null if the file does not exist at that ref (404) -
     * callers must handle null explicitly rather than treating it as an error.
     */
    @SuppressWarnings("unchecked")
    public FileContentResult getFileContent(String repoFullName, String path, String ref) {
        String url = String.format("%s/repos/%s/contents/%s?ref=%s", API_BASE, repoFullName, path, ref);
        try {
            ResponseEntity<Map> response = githubRestTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            checkRateLimit(response);
            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            String base64Content = (String) body.get("content");
            String sha = (String) body.get("sha");
            String decoded = new String(
                    Base64.getMimeDecoder().decode(base64Content.replace("\n", "")),
                    StandardCharsets.UTF_8
            );
            return new FileContentResult(decoded, sha);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /**
     * Fetches the list of files changed in a specific commit, along with
     * their diff patches, via GitHub's Commits API.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCommitFiles(String repoFullName, String commitSha) {
        String url = String.format("%s/repos/%s/commits/%s", API_BASE, repoFullName, commitSha);
        ResponseEntity<Map> response = githubRestTemplate.exchange(url, HttpMethod.GET, null, Map.class);
        checkRateLimit(response);
        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("files")) {
            return List.of();
        }
        return (List<Map<String, Object>>) body.get("files");
    }

    @SuppressWarnings("unchecked")
    public String getBranchHeadSha(String repoFullName, String branch) {
        String url = String.format("%s/repos/%s/git/ref/heads/%s", API_BASE, repoFullName, branch);
        ResponseEntity<Map> response = githubRestTemplate.exchange(url, HttpMethod.GET, null, Map.class);
        checkRateLimit(response);
        Map<String, Object> object = (Map<String, Object>) response.getBody().get("object");
        return (String) object.get("sha");
    }

    public boolean branchExists(String repoFullName, String branch) {
        String url = String.format("%s/repos/%s/git/ref/heads/%s", API_BASE, repoFullName, branch);
        try {
            githubRestTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    public void createBranch(String repoFullName, String newBranch, String baseBranch) {
        String baseSha = getBranchHeadSha(repoFullName, baseBranch);
        String url = String.format("%s/repos/%s/git/refs", API_BASE, repoFullName);

        Map<String, Object> requestBody = Map.of(
                "ref", "refs/heads/" + newBranch,
                "sha", baseSha
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody);
        ResponseEntity<Map> response = githubRestTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        checkRateLimit(response);
    }

    /**
     * Creates or updates a file on a specific branch. Pass currentFileSha
     * when updating an existing file (required by GitHub's API); pass
     * null only when creating a genuinely new file.
     */
    public void updateFile(String repoFullName, String path, String branch, String content,
                           String currentFileSha, String commitMessage) {
        String url = String.format("%s/repos/%s/contents/%s", API_BASE, repoFullName, path);
        String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> requestBody = currentFileSha != null
                ? Map.of("message", commitMessage, "content", base64Content, "sha", currentFileSha, "branch", branch)
                : Map.of("message", commitMessage, "content", base64Content, "branch", branch);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody);
        ResponseEntity<Map> response = githubRestTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
        checkRateLimit(response);
    }

    @SuppressWarnings("unchecked")
    public String createPullRequest(String repoFullName, String title, String body, String head, String base) {
        String url = String.format("%s/repos/%s/pulls", API_BASE, repoFullName);

        Map<String, Object> requestBody = Map.of(
                "title", title, "body", body, "head", head, "base", base
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody);
        ResponseEntity<Map> response = githubRestTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        checkRateLimit(response);
        return (String) response.getBody().get("html_url");
    }

    private void checkRateLimit(ResponseEntity<?> response) {
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        if (remaining != null) {
            try {
                int remainingCalls = Integer.parseInt(remaining);
                if (remainingCalls < 100) {
                    log.warn("GitHub API rate limit low: {} calls remaining", remainingCalls);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public record FileContentResult(String content, String sha) {}
}