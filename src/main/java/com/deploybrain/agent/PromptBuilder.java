package com.deploybrain.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT_PATH = "prompts/system_prompt.txt";
    private String cachedSystemPrompt;

    public String loadSystemPrompt() {
        if (cachedSystemPrompt != null) {
            return cachedSystemPrompt;
        }
        try (InputStream is = new ClassPathResource(SYSTEM_PROMPT_PATH).getInputStream()) {
            cachedSystemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return cachedSystemPrompt;
        } catch (IOException e) {
            log.error("Failed to load system prompt from {}: {}", SYSTEM_PROMPT_PATH, e.getMessage());
            throw new IllegalStateException("system_prompt.txt could not be loaded from classpath", e);
        }
    }

    public String buildUserPrompt(FailureContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("A CI build has failed and needs investigation.\n\n");
        sb.append("Build ID: ").append(context.getBuildId()).append("\n");
        sb.append("Repository: ").append(context.getRepoFullName()).append("\n");
        sb.append("Commit SHA: ").append(context.getCommitSha()).append("\n");
        sb.append("Workflow file that triggered this build: ")
                .append(context.getWorkflowFilePath() != null ? context.getWorkflowFilePath() : "unknown")
                .append("\n");
        sb.append("Failure type (from ML classifier): ").append(context.getFailureType()).append("\n");
        sb.append("Classifier confidence: ").append(String.format("%.0f%%", context.getConfidence() * 100)).append("\n\n");
        sb.append("Evidence lines from the build log:\n```\n");
        if (context.getEvidenceLines() != null && !context.getEvidenceLines().isEmpty()) {
            for (String line : context.getEvidenceLines()) {
                sb.append(line).append("\n");
            }
        } else {
            sb.append("(no specific evidence lines extracted)\n");
        }
        sb.append("```\n\n");
        sb.append("Investigate this failure using the available tools, determine the root cause, ");
        sb.append("and either propose a fix or mark this for human review if you cannot confidently resolve it.");
        return sb.toString();
    }
}