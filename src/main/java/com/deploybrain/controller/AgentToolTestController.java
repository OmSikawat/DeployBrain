package com.deploybrain.controller;

import com.deploybrain.tool.ToolExecutionException;
import com.deploybrain.tool.ToolRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * TEMPORARY controller for manually testing individual agent tools in
 * isolation, before Day 14's orchestrator exists to call them as part of
 * a real ReAct loop. Safe to delete once Day 14 is built.
 */
@RestController
@RequestMapping("/api/test/tools")
public class AgentToolTestController {

    private final ToolRegistry toolRegistry;

    public AgentToolTestController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostMapping("/{toolName}")
    public ResponseEntity<String> testTool(@PathVariable String toolName, @RequestBody Map<String, Object> input) {
        return toolRegistry.getByName(toolName)
                .map(tool -> {
                    try {
                        return ResponseEntity.ok(tool.execute(input));
                    } catch (ToolExecutionException e) {
                        return ResponseEntity.badRequest().body("Tool execution failed: " + e.getMessage());
                    }
                })
                .orElseGet(() -> ResponseEntity.badRequest().body("Unknown tool: " + toolName));
    }

    @GetMapping
    public ResponseEntity<?> listTools() {
        return ResponseEntity.ok(toolRegistry.getAllToolDefinitions());
    }
}