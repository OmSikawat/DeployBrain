package com.deploybrain.controller;

import com.deploybrain.dto.BuildResponse;
import com.deploybrain.dto.StatsResponse;
import com.deploybrain.service.DashboardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/builds")
    public Page<BuildResponse> getBuilds(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return dashboardService.getBuilds(PageRequest.of(page, size));
    }

    @GetMapping("/builds/{id}")
    public ResponseEntity<BuildResponse> getBuildDetail(@PathVariable UUID id) {
        return dashboardService.getBuildDetail(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/failure-types")
    public StatsResponse.FailureTypeStats getFailureTypeStats() {
        return dashboardService.getFailureTypeStats();
    }

    @GetMapping("/stats/mttr")
    public StatsResponse.MttrStats getMttrStats() {
        return dashboardService.getMttrStats();
    }

    @GetMapping("/stats/fix-rate")
    public StatsResponse.FixRateStats getFixRateStats() {
        return dashboardService.getFixRateStats();
    }
}