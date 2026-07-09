package com.deploybrain.service;

import com.deploybrain.entity.Build;
import com.deploybrain.entity.LogChunk;
import com.deploybrain.repository.BuildRepository;
import com.deploybrain.repository.LogChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LogChunkingService {

    private static final int LINES_PER_CHUNK = 500;

    private final LogChunkRepository logChunkRepository;
    private final BuildRepository buildRepository;

    public LogChunkingService(LogChunkRepository logChunkRepository, BuildRepository buildRepository) {
        this.logChunkRepository = logChunkRepository;
        this.buildRepository = buildRepository;
    }

    public void chunkAndSave(Build build, Map<String, String> jobLogs) {

        if (jobLogs.isEmpty()) {
            log.warn("No job logs to chunk for build {} - marking build as ERROR", build.getId());
            build.setStatus(Build.BuildStatus.ERROR);
            buildRepository.save(build);
            return;
        }

        int totalChunksSaved = 0;
        long totalBytes = 0;

        for (Map.Entry<String, String> jobEntry : jobLogs.entrySet()) {
            String jobName = jobEntry.getKey();
            String content = jobEntry.getValue();
            totalBytes += content.getBytes().length;

            List<String> lines = List.of(content.split("\n", -1));
            List<List<String>> segments = splitIntoSegments(lines, LINES_PER_CHUNK);
            int totalChunksForJob = segments.size();

            for (int i = 0; i < segments.size(); i++) {
                String chunkContent = String.join("\n", segments.get(i));

                LogChunk chunk = LogChunk.builder()
                        .build(build)
                        .chunkIndex(i)
                        .totalChunks(totalChunksForJob)
                        .jobName(jobName)
                        .content(chunkContent)
                        .build();

                logChunkRepository.save(chunk);
                totalChunksSaved++;
            }
        }

        build.setLogSizeBytes(totalBytes);
        build.setStatus(Build.BuildStatus.LOGS_CHUNKED);
        buildRepository.save(build);

        log.info("Chunked {} log chunk(s) across {} job(s) for build {}",
                totalChunksSaved, jobLogs.size(), build.getId());
    }

    private List<List<String>> splitIntoSegments(List<String> lines, int segmentSize) {
        List<List<String>> segments = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += segmentSize) {
            segments.add(lines.subList(i, Math.min(i + segmentSize, lines.size())));
        }
        if (segments.isEmpty()) {
            segments.add(List.of(""));
        }
        return segments;
    }
}