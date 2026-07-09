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
            byte[] zipBytes = gitHubApiClient.downloadLogZip(build.getLogsUrl());

            s3LogArchiveService.archiveLogZip(build.getRepoName(), build.getId(), zipBytes);

            Map<String, String> jobLogs = unzipLogs(zipBytes, build.getId());

            logChunkingService.chunkAndSave(build, jobLogs);

        } catch (Exception e) {
            log.error("Failed to process logs for build {}: {}", build.getId(), e.getMessage(), e);
            build.setStatus(Build.BuildStatus.ERROR);
            buildRepository.save(build);
        }
    }

    private Map<String, String> unzipLogs(byte[] zipBytes, java.util.UUID buildId) {
        Map<String, String> jobLogs = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                if (entry.isDirectory()) {
                    continue;
                }
                if (!entry.getName().endsWith(".txt")) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String content = baos.toString(StandardCharsets.UTF_8);

                if (content.getBytes(StandardCharsets.UTF_8).length > WARN_LOG_SIZE_BYTES) {
                    log.warn("Log file {} for build {} exceeds {}MB - proceeding without truncation (hard limit added Day 16)",
                            entry.getName(), buildId, WARN_LOG_SIZE_BYTES / (1024 * 1024));
                }

                String jobName = extractJobName(entry.getName());
                jobLogs.put(jobName, content);
            }
        } catch (IOException e) {
            log.error("Failed to unzip log archive for build {}: {}", buildId, e.getMessage());
            throw new LogFetchException("Corrupted or incomplete log archive", e);
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