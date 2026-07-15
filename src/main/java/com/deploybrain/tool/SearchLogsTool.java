package com.deploybrain.tool;

import com.deploybrain.dto.SimilarChunkResult;
import com.deploybrain.entity.LogChunk;
import com.deploybrain.repository.LogChunkRepository;
import com.deploybrain.service.LogSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SearchLogsTool implements AgentTool {

    private static final int TOP_K = 3;

    private final LogSearchService logSearchService;
    private final LogChunkRepository logChunkRepository;

    public SearchLogsTool(LogSearchService logSearchService, LogChunkRepository logChunkRepository) {
        this.logSearchService = logSearchService;
        this.logChunkRepository = logChunkRepository;
    }

    @Override
    public String getName() {
        return "search_logs";
    }

    @Override
    public String getDescription() {
        return "Searches the build's log content for a given natural language query. "
                + "Use this to find the specific log lines most relevant to understanding the failure.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "build_id", Map.of("type", "string", "description", "The build's UUID"),
                        "query", Map.of("type", "string", "description", "Search query, e.g. 'dependency resolution error'")
                ),
                "required", List.of("build_id", "query")
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws ToolExecutionException {
        String buildIdStr = (String) input.get("build_id");
        String query = (String) input.get("query");

        if (buildIdStr == null || query == null) {
            throw new ToolExecutionException("search_logs requires 'build_id' and 'query' parameters");
        }

        UUID buildId;
        try {
            buildId = UUID.fromString(buildIdStr);
        } catch (IllegalArgumentException e) {
            throw new ToolExecutionException("Invalid build_id format: " + buildIdStr);
        }

        try {
            List<SimilarChunkResult> results = logSearchService.search(buildId, query, TOP_K);

            if (!results.isEmpty()) {
                log.info("search_logs: vector search succeeded for build {}", buildId);
                return formatResults(results);
            }

            log.warn("search_logs: vector search returned no results for build {} "
                    + "(likely embedding unavailable) - falling back to keyword search", buildId);
            return keywordFallbackSearch(buildId, query);

        } catch (Exception e) {
            log.error("search_logs tool failed for build {}: {}", buildId, e.getMessage());
            throw new ToolExecutionException("Failed to search logs: " + e.getMessage(), e);
        }
    }

    /**
     * Option B fallback: when vector search yields nothing (typically
     * because Gemini embeddings failed due to rate limiting during
     * ingestion, or the query itself failed to embed), fall back to a
     * simple case-insensitive keyword match across chunk content so the
     * tool remains useful regardless of embedding availability.
     */
    private String keywordFallbackSearch(UUID buildId, String query) {
        List<LogChunk> chunks = logChunkRepository.findByBuildIdOrderByChunkIndex(buildId);
        String[] keywords = query.toLowerCase().split("\\s+");

        List<LogChunk> matches = chunks.stream()
                .filter(chunk -> {
                    String contentLower = chunk.getContent().toLowerCase();
                    for (String keyword : keywords) {
                        if (keyword.length() > 2 && contentLower.contains(keyword)) {
                            return true;
                        }
                    }
                    return false;
                })
                .limit(TOP_K)
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            return "No matching log content found for query: '" + query + "' (keyword fallback search, no chunks matched)";
        }

        StringBuilder sb = new StringBuilder("Results (keyword fallback search):\n\n");
        for (LogChunk chunk : matches) {
            sb.append("--- ").append(chunk.getJobName()).append(" ---\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String formatResults(List<SimilarChunkResult> results) {
        StringBuilder sb = new StringBuilder("Results (semantic vector search):\n\n");
        for (SimilarChunkResult result : results) {
            sb.append("--- ").append(result.jobName())
                    .append(" (similarity: ").append(String.format("%.2f", result.similarity())).append(") ---\n");
            sb.append(result.content()).append("\n\n");
        }
        return sb.toString();
    }
}