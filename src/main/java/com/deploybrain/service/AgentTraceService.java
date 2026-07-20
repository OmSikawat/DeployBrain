package com.deploybrain.service;

import com.deploybrain.entity.AgentTrace;
import com.deploybrain.entity.Failure;
import com.deploybrain.repository.AgentTraceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentTraceService {

    private final AgentTraceRepository agentTraceRepository;
    private final AgentTracePublisher agentTracePublisher;

    public AgentTraceService(AgentTraceRepository agentTraceRepository, AgentTracePublisher agentTracePublisher) {
        this.agentTraceRepository = agentTraceRepository;
        this.agentTracePublisher = agentTracePublisher;
    }

    public void saveStep(Failure failure, int stepIndex, String thought, String toolName,
                         String toolInput, String toolOutput, String llmProvider, long durationMs) {
        AgentTrace trace = AgentTrace.builder()
                .failure(failure)
                .stepIndex(stepIndex)
                .thought(thought)
                .toolName(toolName)
                .toolInput(toolInput)
                .toolOutput(toolOutput)
                .llmProvider(llmProvider)
                .durationMs(durationMs)
                .build();

        AgentTrace saved = agentTraceRepository.save(trace);
        log.info("Saved agent trace step {} for failure {}: tool={}", stepIndex, failure.getId(), toolName);

        agentTracePublisher.publish(saved);
    }
}