package com.deploybrain.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class LlmRetryHandler {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_MS = 1000;
    private static final int MAX_HISTORY_MESSAGE_CHARS = 4000; // Groq TPM=8000 tokens, keep messages lean

    public static LlmResponse callWithRetry(LlmClient client, String systemPrompt,
                                            List<Message> history, List<ToolDefinition> tools) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                truncateHistoryForTokenBudget(history);
                return client.generateWithTools(systemPrompt, history, tools);
            } catch (Exception e) {
                lastException = e;

                // IMPORTANT: Check context-too-long FIRST because Groq's 413 error
                // body contains "rate_limit_exceeded" which would falsely match a
                // rate-limit check. We must distinguish between "payload too big"
                // (needs truncation) vs "too many requests" (needs sleep).
                boolean looksLikeContextTooLong = e.getMessage() != null
                    && (e.getMessage().contains("413")
                        || e.getMessage().toLowerCase().contains("too large")
                        || (e.getMessage().contains("400") && e.getMessage().toLowerCase().contains("context")));

                boolean isRateLimit = !looksLikeContextTooLong && e.getMessage() != null
                    && (e.getMessage().contains("429") || e.getMessage().toLowerCase().contains("too many requests"));

                if (isRateLimit) {
                    // Parse the "retry in Xs" delay from the error message and wait.
                    // Both Gemini and Groq include this in their 429 responses.
                    long rateLimitDelayMs = 60_000; // default 60s
                    String msg = e.getMessage();
                    if (msg != null) {
                        // Match patterns like "retry in 53.77s" or "try again in 20.79s"
                        java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("(?:retry|try again) in (\\d+(?:\\.\\d+)?)s")
                            .matcher(msg.toLowerCase());
                        if (m.find()) {
                            rateLimitDelayMs = (long) (Double.parseDouble(m.group(1)) * 1000) + 2000;
                        }
                    }
                    log.warn("Rate limit hit. Sleeping {}ms until quota resets...", rateLimitDelayMs);
                    try {
                        Thread.sleep(rateLimitDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for rate limit reset", ie);
                    }
                    // After sleeping, continue the retry loop — the rate limit should have reset
                    continue;
                }

                if (looksLikeContextTooLong) {
                    truncateHistoryForTokenBudget(history);
                }

                if (attempt < MAX_ATTEMPTS) {
                    long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                    long jitter = ThreadLocalRandom.current().nextLong(0, 500);
                    try {
                        Thread.sleep(delay + jitter);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    log.warn("LLM call attempt {} failed ({}), retrying after {}ms", attempt, e.getMessage(), delay + jitter);
                }
            }
        }

        throw new IllegalStateException("LLM call failed after " + MAX_ATTEMPTS + " attempts", lastException);
    }

    /**
     * Groq's free tier caps at 8000 tokens per minute. A growing tool-call
     * history with full log dumps and file contents can blow past this
     * fast. Proactively truncate large messages BEFORE hitting the limit,
     * not just reactively after a 400/413.
     */
    private static void truncateHistoryForTokenBudget(List<Message> history) {
        for (Message msg : history) {
            if (msg.getContent() != null && msg.getContent().length() > MAX_HISTORY_MESSAGE_CHARS) {
                String content = msg.getContent();
                msg.setContent(content.substring(0, MAX_HISTORY_MESSAGE_CHARS / 2)
                    + "\n\n...[truncated to fit token budget]...\n\n"
                    + content.substring(content.length() - MAX_HISTORY_MESSAGE_CHARS / 2));
            }
        }
        if (history.size() > 6) {
            // drop oldest tool exchanges first, keep the most recent context
            history.remove(1);
            if (history.size() > 6) history.remove(1);
        }
    }
}