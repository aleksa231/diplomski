package com.benchmark.runner.service;

import com.benchmark.runner.model.ExperimentDtos.AggregateStats;
import com.benchmark.runner.model.ExperimentDtos.SampleMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Mean and standard deviation across repetitions (spec section 10.1). Resource metrics are optional:
 * when Prometheus was unavailable they are {@code null} and are simply skipped instead of turning
 * into a misleading zero.
 */
@Component
public class ResultAggregator {

    private final Map<String, ToDoubleFunction<SampleMetrics>> metrics = new LinkedHashMap<>();

    public ResultAggregator() {
        metrics.put("rps", SampleMetrics::getRps);
        metrics.put("latencyMean", SampleMetrics::getLatencyMean);
        metrics.put("latencyP50", SampleMetrics::getLatencyP50);
        metrics.put("latencyP95", SampleMetrics::getLatencyP95);
        metrics.put("latencyP99", SampleMetrics::getLatencyP99);
        metrics.put("errorRate", SampleMetrics::getErrorRate);
        metrics.put("cpuAvg", s -> s.getCpuAvg() == null ? Double.NaN : s.getCpuAvg());
        metrics.put("memAvg", s -> s.getMemAvg() == null ? Double.NaN : s.getMemAvg());
    }

    public Map<String, AggregateStats> aggregate(List<SampleMetrics> samples) {
        Map<String, AggregateStats> result = new LinkedHashMap<>();
        if (samples == null || samples.isEmpty()) {
            return result;
        }
        metrics.forEach((name, extractor) -> {
            double[] values = samples.stream()
                    .mapToDouble(extractor)
                    .filter(v -> !Double.isNaN(v))
                    .toArray();
            if (values.length > 0) {
                result.put(name, AggregateStats.builder()
                        .mean(mean(values))
                        .stdDev(stdDev(values))
                        .build());
            }
        });
        return result;
    }

    private double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private double stdDev(double[] values) {
        if (values.length < 2) {
            return 0.0;
        }
        double mean = mean(values);
        double sumSquares = 0;
        for (double value : values) {
            double diff = value - mean;
            sumSquares += diff * diff;
        }
        // Sample standard deviation: repetitions are a sample of the run-to-run distribution.
        return Math.sqrt(sumSquares / (values.length - 1));
    }
}
