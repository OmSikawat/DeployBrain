package com.deploybrain.service;

import com.deploybrain.entity.AgentTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentTracePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public AgentTracePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(AgentTrace trace) {
        try {
            messagingTemplate.convertAndSend("/topic/trace/" + trace.getFailure().getId(), trace);
        } catch (Exception e) {
            // Live streaming is a UX enhancement, not core pipeline logic -
            // never let a WebSocket publish failure affect the actual
            // agent run, which already fully persisted this step.
            log.warn("Failed to publish trace step to WebSocket: {}", e.getMessage());
        }
    }
}