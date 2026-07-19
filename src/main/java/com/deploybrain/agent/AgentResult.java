package com.deploybrain.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResult {
    public enum Status { FIX_GENERATED, NEEDS_REVIEW, ERROR }

    private Status status;
    private String prUrl;
    private String diagnosis;
    private String providerUsed;
    private String reason;
}