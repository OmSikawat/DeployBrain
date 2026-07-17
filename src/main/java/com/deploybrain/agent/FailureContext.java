package com.deploybrain.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailureContext {
    private UUID failureId;
    private UUID buildId;
    private String repoFullName;
    private String owner;
    private String commitSha;
    private String failureType;
    private double confidence;
    private List<String> evidenceLines;

    public static String extractOwner(String repoFullName) {
        if (repoFullName == null || !repoFullName.contains("/")) return "";
        return repoFullName.split("/")[0];
    }
}