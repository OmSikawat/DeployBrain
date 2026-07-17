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
public class Message {
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL_RESULT }

    private Role role;
    private String content;
    private String toolName; // set for ASSISTANT (tool-call turn) and TOOL_RESULT roles
}