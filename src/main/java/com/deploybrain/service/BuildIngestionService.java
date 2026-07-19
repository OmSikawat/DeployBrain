package com.deploybrain.service;

import com.deploybrain.dto.WebhookPayload;
import com.deploybrain.entity.Build;
import com.deploybrain.repository.BuildRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class BuildIngestionService {

    private final BuildRepository buildRepository;
    private final GitHubLogFetcherService gitHubLogFetcherService;

    public BuildIngestionService(BuildRepository buildRepository, GitHubLogFetcherService gitHubLogFetcherService) {
        this.buildRepository = buildRepository;
        this.gitHubLogFetcherService = gitHubLogFetcherService;
    }

    public Optional<Build> ingestBuild(WebhookPayload payload) {

        Long workflowRunId = payload.getWorkflowRun().getId();

        if (buildRepository.findByWorkflowRunId(workflowRunId).isPresent()) {
            log.info("Build for workflow_run_id {} already exists, skipping duplicate", workflowRunId);
            return Optional.empty();
        }

        Build build = Build.builder()
                .repoName(payload.getRepository().getFullName())
                .repoOwner(payload.getRepository().getOwner().getLogin())
                .commitSha(payload.getWorkflowRun().getHeadSha())
                .workflowName(payload.getWorkflowRun().getName())
                .workflowFilePath(payload.getWorkflowRun().getPath())
                .workflowRunId(workflowRunId)
                .logsUrl(payload.getWorkflowRun().getLogsUrl())
                .status(Build.BuildStatus.RECEIVED)
                .build();

        try {
            Build saved = buildRepository.save(build);
            log.info("Ingested new build: repo={}, workflowRunId={}, buildId={}",
                    saved.getRepoName(), saved.getWorkflowRunId(), saved.getId());

            gitHubLogFetcherService.processBuildLogs(saved);

            return Optional.of(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for workflow_run_id {} - already saved by concurrent request", workflowRunId);
            return Optional.empty();
        }
    }
}