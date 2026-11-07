package com.benchmark.runner.service;

import com.benchmark.runner.metrics.PrometheusClient;
import com.benchmark.runner.model.ExperimentDtos.TimelinePoint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the per-second timeline required for the resilience runs (S6c failover, S6d Redis down):
 * rps, server errors and p95 straight from Prometheus, so the recovery shape is visible even though
 * k6 only reports an aggregate.
 */
@Component
public class TimelineCollector {

    private static final int STEP_SEC = 1;
    private static final String APPLICATION = "application";

    private final PrometheusClient prometheus;

    public TimelineCollector(PrometheusClient prometheus) {
        this.prometheus = prometheus;
    }

    public List<TimelinePoint> collect(String application, Instant start, Instant end) {
        List<Double> rps = prometheus.rangeValues(
                "sum(rate(http_server_requests_seconds_count{" + APPLICATION + "=\"" + application + "\"}[30s]))",
                start, end, STEP_SEC);
        List<Double> errors = prometheus.rangeValues(
                "sum(rate(http_server_requests_seconds_count{" + APPLICATION + "=\"" + application
                        + "\",outcome=\"SERVER_ERROR\"}[30s]))",
                start, end, STEP_SEC);
        List<Double> p95 = prometheus.rangeValues(
                "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{"
                        + APPLICATION + "=\"" + application + "\"}[30s]))) * 1000",
                start, end, STEP_SEC);

        int points = Math.max(rps.size(), Math.max(errors.size(), p95.size()));
        List<TimelinePoint> timeline = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            timeline.add(TimelinePoint.builder()
                    .secondOffset(i * STEP_SEC)
                    .rps(at(rps, i))
                    .serverErrors(at(errors, i))
                    .p95Ms(at(p95, i))
                    .build());
        }
        return timeline;
    }

    private double at(List<Double> values, int index) {
        return index < values.size() ? values.get(index) : 0.0;
    }
}
