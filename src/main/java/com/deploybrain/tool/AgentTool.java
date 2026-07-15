package com.deploybrain.tool;

import java.util.Map;

public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    String execute(Map<String, Object> input) throws ToolExecutionException;
}