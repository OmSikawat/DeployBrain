package com.deploybrain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassifyResponse {

    @JsonProperty("failure_type")
    private String failureType;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("evidence_lines")
    private List<String> evidenceLines;
}