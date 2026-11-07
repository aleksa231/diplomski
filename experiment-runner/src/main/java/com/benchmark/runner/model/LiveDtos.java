package com.benchmark.runner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload of {@code GET /api/live}: current rps, p95, cache hit ratio and service health,
 * derived from Prometheus and the services' actuator endpoints (spec section 10.2).
 */
public final class LiveDtos {

    private LiveDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealth {
        private String name;
        private String url;
        /** UP, DOWN, or the raw actuator status when it differs. */
        private String status;
        private String detail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceMetrics {
        private String application;
        private Double rps;
        private Double p95Ms;
        /** Null for services without a catalog cache. */
        private Double cacheHitRatio;
        private Double cpuCores;
        private Double memoryMb;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveStatus {
        private String timestamp;
        private boolean prometheusAvailable;
        @Builder.Default
        private List<ServiceHealth> services = new ArrayList<>();
        @Builder.Default
        private List<ServiceMetrics> metrics = new ArrayList<>();
    }
}
