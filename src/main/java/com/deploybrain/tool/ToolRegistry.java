package com.deploybrain.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName;

    public ToolRegistry(List<AgentTool> tools) {
        this.toolsByName = tools.stream()
                .collect(Collectors.toMap(AgentTool::getName, t -> t));
        log.info("ToolRegistry initialized with {} tools: {}", toolsByName.size(), toolsByName.keySet());
    }

    public Optional<AgentTool> getByName(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<Map<String, Object>> getAllToolDefinitions() {
        return toolsByName.values().stream()
                .map(t -> Map.<String, Object>of(
                        "name", t.getName(),
                        "description", t.getDescription(),
                        "input_schema", t.getInputSchema()
                ))
                .collect(Collectors.toList());
    }
}