package com.deploybrain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

public class StatsResponse {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureTypeStats {
        private Map<String, Long> countsByType;
        private long totalBuilds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MttrStats {
        private List<WeekPoint> trend;
        private double overallAvgMinutes;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class WeekPoint {
            private String weekLabel;
            private double avgMinutes;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixRateStats {
        private Map<String, Double> fixRateByType;
        private long totalFixGenerated;
        private long totalNeedsReview;
        private double overallAutoFixRate;
    }
}