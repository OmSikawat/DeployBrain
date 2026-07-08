package com.deploybrain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    @JsonProperty("action")
    private String action;

    @JsonProperty("repository")
    private Repository repository;

    @JsonProperty("workflow_run")
    private WorkflowRun workflowRun;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("owner")
        private Owner owner;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Owner {

        @JsonProperty("login")
        private String login;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowRun {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("head_sha")
        private String headSha;

        @JsonProperty("conclusion")
        private String conclusion;

        @JsonProperty("logs_url")
        private String logsUrl;
    }
}