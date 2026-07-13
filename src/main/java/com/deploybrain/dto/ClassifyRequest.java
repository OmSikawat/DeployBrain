package com.deploybrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClassifyRequest {

    @JsonProperty("log_text")
    private String logText;

    @JsonProperty("build_id")
    private String buildId;
}