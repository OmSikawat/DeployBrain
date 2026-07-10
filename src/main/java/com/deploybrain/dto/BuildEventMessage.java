package com.deploybrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BuildEventMessage {

    @JsonProperty("buildId")
    private UUID buildId;

    @JsonProperty("repoName")
    private String repoName;

    @JsonProperty("chunkedAt")
    private LocalDateTime chunkedAt;
}