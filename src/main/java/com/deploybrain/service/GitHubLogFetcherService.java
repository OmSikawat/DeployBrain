package com.deploybrain.service;

import com.deploybrain.entity.Build;
import com.deploybrain.repository.BuildRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class GitHubLogFetcherService {

    private static final long WARN_LOG_SIZE_BYTES = 20L * 1024 * 1024;
    // 20MB soft warning threshold today.
    // Hard truncation to last 2000 lines is added Day 16 via LogSizeLimiter.

    private final GitHubApiClient gitHubApiClient;
    private final S3LogArchiveService s3LogArchiveService;
    private final LogChunkingService logChunkingService;
    private final BuildRepository buildRepository;

    public GitHubLogFetcherService(
            GitHubApiClient gitHubApiClient,
            S3LogArchiveService s3LogArchiveService,
            LogChunkingService logChunkingService,
            BuildRepository buildRepository
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.s3LogArchiveService = s3LogArchiveService;
        this.logChunkingService = logChunkingService;
        this.buildRepository = buildRepository;
    }

    /**
     * Entry point called asynchronously right after a Build is ingested.
     * Fetches logs from GitHub, archives raw zip to S3/MinIO, unzips,
     * and hands extracted per-job log text to LogChunkingService.
     */
    @Async
    public void processBuildLogs(Build build) {
        try {
            // GitHub's log archive can lag slightly behind the workflow_run
            // webhook firing, especially for very short-lived jobs. A brief
            // delay reduces the risk of fetching a partially-written log.
            Thread.sleep(5000);

            byte[] zipBytes = gitHubApiClient.downloadLogZip(build.getLogsUrl());

            s3LogArchiveService.archiveLogZip(build.getRepoName(), build.getId(), zipBytes);

            Map<String, String> jobLogs = unzipLogs(zipBytes, build.getId());

            // NEW: flag suspiciously small logs for visibility
            for (Map.Entry<String, String> entry : jobLogs.entrySet()) {
                if (entry.getValue().length() < 1000) {
                    log.warn("Suspiciously small log ({} chars) for job '{}' in build {} - may indicate incomplete log fetch",
                            entry.getValue().length(), entry.getKey(), build.getId());
                }
            }

            logChunkingService.chunkAndSave(build, jobLogs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting before log fetch for build {}", build.getId());
        } catch (Exception e) {
            log.error("Failed to process logs for build {}: {}", build.getId(), e.getMessage(), e);
            build.setStatus(Build.BuildStatus.ERROR);
            buildRepository.save(build);
        }
    }

//    private Map<String, String> unzipLogs(byte[] zipBytes, java.util.UUID buildId) {
//        Map<String, String> jobLogs = new LinkedHashMap<>();
//
//        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
//            ZipEntry entry;
//            while ((entry = zis.getNextEntry()) != null) {
//
//                if (entry.isDirectory()) {
//                    continue;
//                }
//                if (!entry.getName().endsWith(".txt")) {
//                    continue;
//                }
//
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                byte[] buffer = new byte[4096];
//                int len;
//                while ((len = zis.read(buffer)) > 0) {
//                    baos.write(buffer, 0, len);
//                }
//
//                String content = baos.toString(StandardCharsets.UTF_8);
//
//                if (content.getBytes(StandardCharsets.UTF_8).length > WARN_LOG_SIZE_BYTES) {
//                    log.warn("Log file {} for build {} exceeds {}MB - proceeding without truncation (hard limit added Day 16)",
//                            entry.getName(), buildId, WARN_LOG_SIZE_BYTES / (1024 * 1024));
//                }
//
//                String jobName = extractJobName(entry.getName());
//                jobLogs.put(jobName, content);
//            }
//        } catch (IOException e) {
//            log.error("Failed to unzip log archive for build {}: {}", buildId, e.getMessage());
//            throw new LogFetchException("Corrupted or incomplete log archive", e);
//        }
//
//        if (jobLogs.isEmpty()) {
//            log.warn("No log content found in zip for build {} - workflow may have failed before any job started", buildId);
//        }
//
//        return jobLogs;
//    }

//    private Map<String, String> unzipLogs(byte[] zipBytes, java.util.UUID buildId) {
//        Map<String, String> jobLogs = new LinkedHashMap<>();
//
//        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
//            ZipEntry entry;
//            while ((entry = zis.getNextEntry()) != null) {
//
//                if (entry.isDirectory()) {
//                    continue;
//                }
//                if (!entry.getName().endsWith(".txt")) {
//                    continue;
//                }
//
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                byte[] buffer = new byte[4096];
//                int len;
//                while ((len = zis.read(buffer)) > 0) {
//                    baos.write(buffer, 0, len);
//                }
//
//                String content = baos.toString(StandardCharsets.UTF_8);
//                String jobName = extractJobName(entry.getName());
//
//                // TEMPORARY DIAGNOSTIC - remove after confirming the issue
//                log.info("Zip entry: '{}' -> derived jobName: '{}' -> content length: {}",
//                        entry.getName(), jobName, content.length());
//
//                jobLogs.put(jobName, content);
//            }
//        } catch (IOException e) {
//            log.error("Failed to unzip log archive for build {}: {}", buildId, e.getMessage());
//            throw new LogFetchException("Corrupted or incomplete log archive", e);
//        }
//
//        if (jobLogs.isEmpty()) {
//            log.warn("No log content found in zip for build {} - workflow may have failed before any job started", buildId);
//        }
//
//        return jobLogs;
//    }

    private Map<String, String> unzipLogs(byte[] zipBytes, java.util.UUID buildId) {
        Map<String, StringBuilder> jobLogsBuilder = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                if (entry.isDirectory()) {
                    continue;
                }
                if (!entry.getName().endsWith(".txt")) {
                    continue;
                }
                // skip the noisy per-job system metadata file, not real step output
                if (entry.getName().endsWith("/system.txt")) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String content = baos.toString(StandardCharsets.UTF_8);
                String jobName = extractJobName(entry.getName());

                if (content.getBytes(StandardCharsets.UTF_8).length > WARN_LOG_SIZE_BYTES) {
                    log.warn("Log file {} for build {} exceeds {}MB - proceeding without truncation (hard limit added Day 16)",
                            entry.getName(), buildId, WARN_LOG_SIZE_BYTES / (1024 * 1024));
                }

                // APPEND rather than overwrite - concatenate every step's
                // output for a given job into one combined log, in the order
                // the zip entries were encountered (which matches step order).
                jobLogsBuilder
                        .computeIfAbsent(jobName, k -> new StringBuilder())
                        .append("--- ").append(entry.getName()).append(" ---\n")
                        .append(content)
                        .append("\n\n");
            }
        } catch (IOException e) {
            log.error("Failed to unzip log archive for build {}: {}", buildId, e.getMessage());
            throw new LogFetchException("Corrupted or incomplete log archive", e);
        }

        Map<String, String> jobLogs = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> e : jobLogsBuilder.entrySet()) {
            jobLogs.put(e.getKey(), e.getValue().toString());
        }

        if (jobLogs.isEmpty()) {
            log.warn("No log content found in zip for build {} - workflow may have failed before any job started", buildId);
        }

        return jobLogs;
    }

    private String extractJobName(String zipEntryName) {
        // GitHub zip entries look like "1_JobName.txt" or "JobName/1_StepName.txt"
        String name = zipEntryName;
        if (name.contains("/")) {
            name = name.substring(0, name.indexOf("/"));
        }
        name = name.replaceAll("^\\d+_", "");
        name = name.replaceAll("\\.txt$", "");
        return name.isBlank() ? "unknown_job" : name;
    }

    public static class LogFetchException extends RuntimeException {
        public LogFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}