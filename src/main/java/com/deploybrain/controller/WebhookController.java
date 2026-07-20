package com.deploybrain.controller;

import com.deploybrain.dto.WebhookPayload;
import com.deploybrain.service.BuildIngestionService;
import com.deploybrain.service.IdempotencyService;
import com.deploybrain.service.WebhookVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookVerificationService verificationService;
    private final BuildIngestionService buildIngestionService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private static final String CONCLUSION_FAILURE = "failure";
    private static final String CONCLUSION_TIMED_OUT = "timed_out";
    private static final String CONCLUSION_CANCELLED = "cancelled";

    public WebhookController(WebhookVerificationService verificationService,
                             BuildIngestionService buildIngestionService,
                             IdempotencyService idempotencyService,
                             ObjectMapper objectMapper) {
        this.verificationService = verificationService;
        this.buildIngestionService = buildIngestionService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGithubWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = true) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId
    ) {
        if (!verificationService.isValidSignature(rawBody, signature)) {
            log.warn("Webhook signature verification failed for delivery {}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!idempotencyService.isFirstDelivery(deliveryId)) {
            log.info("Duplicate webhook delivery {} - already processed, skipping", deliveryId);
            return ResponseEntity.ok("Duplicate delivery ignored");
        }

        if ("ping".equals(eventType)) {
            log.info("Received ping event - webhook configured correctly");
            return ResponseEntity.ok("pong");
        }

        if (!"workflow_run".equals(eventType)) {
            log.info("Ignoring unhandled event type: {}", eventType);
            return ResponseEntity.ok("Event type ignored");
        }

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse workflow_run payload", e);
            return ResponseEntity.badRequest().body("Malformed payload");
        }

        String conclusion = payload.getWorkflowRun().getConclusion();

        if (!CONCLUSION_FAILURE.equals(conclusion) && !CONCLUSION_TIMED_OUT.equals(conclusion)
                && !CONCLUSION_CANCELLED.equals(conclusion)) {
            log.info("Ignoring workflow_run with conclusion: {}", conclusion);
            return ResponseEntity.ok("Non-failure conclusion ignored");
        }

        buildIngestionService.ingestBuild(payload);

        return ResponseEntity.ok("Build ingested");
    }
}