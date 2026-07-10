package com.deploybrain.dto;

public record SimilarChunkResult(
        String content,
        String jobName,
        double similarity
) {}