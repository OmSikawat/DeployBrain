package com.deploybrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LogSizeLimiter {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
    private static final int KEEP_LAST_LINES = 2000;

    /**
     * If a job's combined log text exceeds 10MB, truncate to the last
     * 2000 lines (where failures almost always are) and prepend a
     * truncation notice. Protects memory during classification, matters
     * most on constrained deployment environments.
     */
    public String limitIfNeeded(String jobName, String content) {
        long sizeBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (sizeBytes <= MAX_SIZE_BYTES) {
            return content;
        }

        log.warn("Log for job '{}' exceeds 10MB ({} bytes) - truncating to last {} lines",
                jobName, sizeBytes, KEEP_LAST_LINES);

        String[] lines = content.split("\n", -1);
        int start = Math.max(0, lines.length - KEEP_LAST_LINES);
        StringBuilder sb = new StringBuilder();
        sb.append("[TRUNCATED - original log exceeded 10MB, showing last ")
                .append(KEEP_LAST_LINES).append(" lines]\n\n");
        for (int i = start; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }
}