package com.deploybrain.agent;

import com.deploybrain.entity.Build;
import com.deploybrain.entity.Failure;
import com.deploybrain.repository.BuildRepository;
import com.deploybrain.repository.FailureRepository;
import com.deploybrain.service.AgentTraceService;
import com.deploybrain.tool.AgentTool;
import com.deploybrain.tool.ToolExecutionException;
import com.deploybrain.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AgentOrchestrator {

    private static final int MAX_TURNS = 15;
    private static final String OPEN_PR_TOOL_NAME = "open_pr";
    private static final String READ_FILE_TOOL_NAME = "read_file";
    private static final String DEFAULT_BASE_BRANCH = "main"; // simplification: assumes main is the default branch


    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final AgentTraceService agentTraceService;
    private final FailureRepository failureRepository;
    private final BuildRepository buildRepository;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            PromptBuilder promptBuilder,
            AgentTraceService agentTraceService,
            FailureRepository failureRepository,
            BuildRepository buildRepository,
            ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.agentTraceService = agentTraceService;
        this.failureRepository = failureRepository;
        this.buildRepository = buildRepository;
        this.objectMapper = objectMapper;
    }

    @Async("agentExecutor")
    @SuppressWarnings("unchecked")
    public void investigateAndFix(Failure failure) {
        Build build = failure.getBuild();
        build.setStatus(Build.BuildStatus.AGENT_RUNNING);
        buildRepository.save(build);

        FailureContext context = buildContext(failure);
        String systemPrompt = promptBuilder.loadSystemPrompt();
        List<Message> history = new ArrayList<>();
        history.add(Message.builder().role(Message.Role.USER).content(promptBuilder.buildUserPrompt(context)).build());

        // open_pr is deliberately excluded from what the LLM can call -
        // the Java orchestrator is always the one to invoke it, after
        // validating the fix is grounded in a real read_file call.
        List<ToolDefinition> availableTools = toolRegistry.getAllToolDefinitions().stream()
                .filter(def -> !OPEN_PR_TOOL_NAME.equals(def.get("name")))
                .map(def -> new ToolDefinition(
                        (String) def.get("name"),
                        (String) def.get("description"),
                        (Map<String, Object>) def.get("input_schema")))
                .toList();

        // Tracks every file_path successfully retrieved via a real
        // read_file tool call during this run. A proposed fix's
        // file_path must appear in this set or it is rejected as
        // ungrounded/hallucinated content before ever reaching OpenPRTool.
        Set<String> filesActuallyRead = new HashSet<>();

        AgentResult result = null;

        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            long startTime = System.currentTimeMillis();
            LlmResponse response;
            try {
                response = LlmRetryHandler.callWithRetry(llmClient, systemPrompt, history, availableTools);
            } catch (Exception e) {
                log.error("LLM call failed permanently on turn {} for failure {}: {}", turn, failure.getId(), e.getMessage());
                result = AgentResult.builder()
                        .status(AgentResult.Status.ERROR)
                        .diagnosis("Agent could not complete investigation due to repeated LLM provider failures")
                        .reason(e.getMessage())
                        .build();
                break;
            }
            long durationMs = System.currentTimeMillis() - startTime;

            if (response.getType() == LlmResponse.ResponseType.TOOL_CALL) {
                if (response.getToolName() == null) {
                    log.warn("Recovered a leaked tool-call argument set but tool name is unknown - re-prompting for turn {}", turn);
                    history.add(Message.builder().role(Message.Role.ASSISTANT).content(response.getTextContent()).build());
                    history.add(Message.builder()
                            .role(Message.Role.USER)
                            .content("Your previous response appears to be tool call arguments without specifying which tool. "
                                    + "Please use the proper tool-calling format to invoke a specific tool, or provide your final "
                                    + "answer as JSON with an 'action' field.")
                            .build());
                    continue;
                }


                String toolOutput = executeToolSafely(response, context);

                // Track successfully-read files for later fix grounding
                // validation. Only counts as "actually read" if the tool
                // call was read_file, had a path input, and did not fail.
                if (READ_FILE_TOOL_NAME.equals(response.getToolName())
                        && response.getToolInput() != null
                        && response.getToolInput().get("path") != null
                        && !toolOutput.startsWith("File not found")
                        && !toolOutput.startsWith("Tool execution error")
                        && !toolOutput.startsWith("Unexpected tool error")
                        && !toolOutput.startsWith("Error: no tool named")) {
                    filesActuallyRead.add((String) response.getToolInput().get("path"));
                }

                agentTraceService.saveStep(failure, turn, null, response.getToolName(),
                        safeToJson(response.getToolInput()), toolOutput, response.getProviderUsed(), durationMs);

                history.add(Message.builder()
                        .role(Message.Role.ASSISTANT)
                        .toolName(response.getToolName())
                        .content(safeToJson(response.getToolInput()))
                        .build());
                history.add(Message.builder()
                        .role(Message.Role.TOOL_RESULT)
                        .toolName(response.getToolName())
                        .content(toolOutput)
                        .build());

            } else {
                agentTraceService.saveStep(failure, turn, response.getTextContent(), null, null, null,
                        response.getProviderUsed(), durationMs);

                result = parseFinalAnswer(response, context, failure, filesActuallyRead);
                if (result == null) {
                    log.warn("Invalid final answer format from agent, prompting to retry. Turn: {}", turn);
                    history.add(Message.builder()
                            .role(Message.Role.ASSISTANT)
                            .content(response.getTextContent())
                            .build());
                    history.add(Message.builder()
                            .role(Message.Role.USER)
                            .content("Your previous response was not a valid tool call, and it did not match the REQUIRED final answer JSON format. You MUST output a JSON object containing an 'action' field (either 'fix' or 'needs_review') along with the required fields like 'diagnosis'. Do not output raw tool arguments as text.")
                            .build());
                    continue;
                }
                break;
            }
        }

        if (result == null) {
            log.warn("Agent exhausted {} turns without a final answer for failure {}", MAX_TURNS, failure.getId());
            result = AgentResult.builder()
                    .status(AgentResult.Status.NEEDS_REVIEW)
                    .diagnosis("Agent could not reach a confident conclusion within the maximum number of investigation steps")
                    .reason("Exceeded max turns (" + MAX_TURNS + ") without a final answer")
                    .build();
        }

        applyResult(failure, build, result);
    }

    private String executeToolSafely(LlmResponse response, FailureContext context) {
        String toolName = response.getToolName();
        Map<String, Object> inputArgs = response.getToolInput() != null ? response.getToolInput() : Map.of();
        
        if (toolName == null) {
            // Infer tool from arguments if missing (supports leaked tool calls from Gemini)
            if (inputArgs.containsKey("query") && inputArgs.containsKey("build_id")) toolName = "search_logs";
            else if (inputArgs.containsKey("path") && inputArgs.containsKey("repo")) toolName = "read_file";
            else if (inputArgs.containsKey("repo") && inputArgs.containsKey("commit_sha")) toolName = "get_diff";
            else if (inputArgs.containsKey("repo") && inputArgs.containsKey("failure_type")) toolName = "get_history";
            else return "Error: You must specify a valid tool name to call.";
        }

        Optional<AgentTool> tool = toolRegistry.getByName(toolName);
        if (tool.isEmpty()) {
            return "Error: no tool named '" + toolName + "' exists. "
                    + "Check the tool name and try again.";
        }
        try {
            Map<String, Object> input = new HashMap<>(inputArgs);
            input.putIfAbsent("build_id", context.getBuildId().toString());
            input.putIfAbsent("repo", context.getRepoFullName());
            input.putIfAbsent("commit_sha", context.getCommitSha());
            input.putIfAbsent("failure_type", context.getFailureType());

            return tool.get().execute(input);
        } catch (ToolExecutionException e) {
            return "Tool execution error: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error executing tool '{}': {}", response.getToolName(), e.getMessage());
            return "Unexpected tool error: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private AgentResult parseFinalAnswer(LlmResponse response, FailureContext context, Failure failure,
                                         Set<String> filesActuallyRead) {
        String text = extractJsonBlock(response.getTextContent());
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(text, Map.class);
        } catch (Exception e) {
            log.warn("Could not parse agent final answer as JSON for failure {}: {}", failure.getId(), e.getMessage());
            return null;
        }

        String action = (String) parsed.get("action");

        if ("fix".equals(action)) {
            String filePath = (String) parsed.get("file_path");
            String fixedContent = (String) parsed.get("fixed_content");
            String fixDescription = (String) parsed.get("fix_description");
            String rootCause = (String) parsed.get("root_cause");
            String diagnosis = (String) parsed.get("diagnosis");

            // GROUNDING CHECK: reject any proposed fix whose file_path was
            // never actually retrieved via a real read_file tool call
            // during this run. Without this, the LLM can hallucinate
            // plausible-looking fixed_content based only on a diff
            // snippet or error message, never having seen the real file -
            // and that fabricated content would otherwise become a real
            // pull request on the repository.
            if (filePath == null || !filesActuallyRead.contains(filePath)) {
                log.warn("Fix rejected for failure {} - file_path '{}' was never read via read_file tool call",
                        failure.getId(), filePath);
                return AgentResult.builder()
                        .status(AgentResult.Status.NEEDS_REVIEW)
                        .diagnosis(diagnosis != null ? diagnosis : "No diagnosis provided")
                        .reason("fix rejected - file_path was never read via read_file tool call")
                        .providerUsed(response.getProviderUsed())
                        .build();
            }

            failure.setRootCause(rootCause);

            Map<String, Object> prInput = new HashMap<>();
            prInput.put("repo", context.getRepoFullName());
            prInput.put("base_branch", DEFAULT_BASE_BRANCH);
            prInput.put("commit_sha", context.getCommitSha());
            prInput.put("file_path", filePath);
            prInput.put("fixed_content", fixedContent);
            prInput.put("failure_type", context.getFailureType());
            prInput.put("confidence", context.getConfidence());
            prInput.put("root_cause", rootCause);
            prInput.put("evidence_lines", String.join("\n",
                    context.getEvidenceLines() != null ? context.getEvidenceLines() : List.of()));
            prInput.put("fix_description", fixDescription);

            String prResult;
            try {
                prResult = toolRegistry.getByName("open_pr")
                        .orElseThrow(() -> new IllegalStateException("open_pr tool not registered"))
                        .execute(prInput);
            } catch (Exception e) {
                prResult = "Tool execution error: " + e.getMessage();
            }

            if (prResult.startsWith("Pull request opened successfully: ")) {
                String prUrl = prResult.substring("Pull request opened successfully: ".length()).trim();
                return AgentResult.builder()
                        .status(AgentResult.Status.FIX_GENERATED)
                        .prUrl(prUrl)
                        .diagnosis(diagnosis)
                        .providerUsed(response.getProviderUsed())
                        .build();
            } else {
                log.warn("Agent proposed a fix but PR creation failed for failure {}: {}", failure.getId(), prResult);
                return AgentResult.builder()
                        .status(AgentResult.Status.NEEDS_REVIEW)
                        .diagnosis(diagnosis + " (Automatic fix attempted but could not be applied: " + prResult + ")")
                        .reason(prResult)
                        .providerUsed(response.getProviderUsed())
                        .build();
            }
        } else if ("needs_review".equals(action)) {
            return AgentResult.builder()
                    .status(AgentResult.Status.NEEDS_REVIEW)
                    .diagnosis((String) parsed.getOrDefault("diagnosis", "No diagnosis provided"))
                    .reason((String) parsed.getOrDefault("reason", "Agent determined this needs human review"))
                    .providerUsed(response.getProviderUsed())
                    .build();
        }

        log.warn("Agent final answer missing valid 'action' field. Parsed keys: {}", parsed.keySet());
        return null;
    }

    private String extractJsonBlock(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) return text;
        return text.substring(start, end + 1);
    }

    private void applyResult(Failure failure, Build build, AgentResult result) {
        failure.setDiagnosis(result.getDiagnosis());
        failure.setLlmProviderUsed(result.getProviderUsed());

        switch (result.getStatus()) {
            case FIX_GENERATED -> {
                failure.setPrUrl(result.getPrUrl());
                failure.setAgentStatus(Failure.AgentStatus.FIX_GENERATED);
                build.setStatus(Build.BuildStatus.AGENT_COMPLETE);
            }
            case NEEDS_REVIEW -> {
                failure.setAgentStatus(Failure.AgentStatus.NEEDS_REVIEW);
                build.setStatus(Build.BuildStatus.NEEDS_REVIEW);
            }
            case ERROR -> {
                failure.setAgentStatus(Failure.AgentStatus.ERROR);
                build.setStatus(Build.BuildStatus.ERROR);
            }
        }

        failureRepository.save(failure);
        buildRepository.save(build);

        log.info("Agent run complete for failure {}: status={}, provider={}",
                failure.getId(), result.getStatus(), result.getProviderUsed());
    }

    private FailureContext buildContext(Failure failure) {
        Build build = failure.getBuild();
        List<String> evidenceLines = failure.getEvidenceLines() != null && !failure.getEvidenceLines().isBlank()
                ? Arrays.asList(failure.getEvidenceLines().split("\n"))
                : List.of();

        return FailureContext.builder()
                .failureId(failure.getId())
                .buildId(build.getId())
                .repoFullName(build.getRepoName())
                .owner(FailureContext.extractOwner(build.getRepoName()))
                .commitSha(build.getCommitSha())
                .workflowFilePath(build.getWorkflowFilePath())
                .failureType(failure.getFailureType().name())
                .confidence(failure.getConfidence())
                .evidenceLines(evidenceLines)
                .build();
    }

    private String safeToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }
}