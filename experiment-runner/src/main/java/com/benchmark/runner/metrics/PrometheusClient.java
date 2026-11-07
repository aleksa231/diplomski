package com.benchmark.runner.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Minimal Prometheus HTTP API client (spec section 10.2: live stats and per-run CPU/memory are
 * derived from Prometheus). All calls are best-effort: when Prometheus is unreachable the runner
 * still produces latency/throughput results, only the resource columns stay empty.
 */
@Component
public class PrometheusClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private final RestClient prometheusRestClient;

    public PrometheusClient(RestClient prometheusRestClient) {
        this.prometheusRestClient = prometheusRestClient;
    }

    /** Instant vector query keyed by a label; empty when Prometheus is down or matched nothing. */
    public Map<String, Double> instantVector(String query, String label) {
        Map<String, Double> result = new LinkedHashMap<>();
        JsonNode data = query("query", Map.of("query", query));
        if (data == null) {
            return result;
        }
        for (JsonNode series : data.path("result")) {
            String key = series.path("metric").path(label).asText(null);
            JsonNode value = series.path("value");
            if (key != null && value.isArray() && value.size() == 2) {
                result.put(key, value.get(1).asDouble());
            }
        }
        return result;
    }

    /** First sample of an instant vector, used for single-series queries. */
    public OptionalDouble instantScalar(String query) {
        JsonNode data = query("query", Map.of("query", query));
        if (data == null) {
            return OptionalDouble.empty();
        }
        for (JsonNode series : data.path("result")) {
            JsonNode value = series.path("value");
            if (value.isArray() && value.size() == 2) {
                return OptionalDouble.of(value.get(1).asDouble());
            }
        }
        return OptionalDouble.empty();
    }

    /** Mean of a range query over {@code [start, end]}, with the given step in seconds. */
    public OptionalDouble rangeAverage(String query, Instant start, Instant end, int stepSec) {
        List<Double> values = rangeValues(query, start, end, stepSec);
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return OptionalDouble.of(sum / values.size());
    }

    /**
     * Values of a range query in ascending time order (first matching series), for timeline charts.
     * Timestamps are dropped because the caller knows the window it asked for.
     */
    public List<Double> rangeValues(String query, Instant start, Instant end, int stepSec) {
        Map<String, String> params = Map.of(
                "query", query,
                "start", String.valueOf(start.getEpochSecond()),
                "end", String.valueOf(end.getEpochSecond()),
                "step", String.valueOf(stepSec) + "s");
        JsonNode data = query("query_range", params);
        if (data == null) {
            return List.of();
        }
        for (JsonNode series : data.path("result")) {
            List<Double> values = new java.util.ArrayList<>();
            for (JsonNode point : series.path("values")) {
                if (point.isArray() && point.size() == 2) {
                    values.add(point.get(1).asDouble());
                }
            }
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    /**
     * Average CPU cores used by a backend over {@code [start, end]}, or empty when not available.
     * <p>
     * The service's own JVM metric is preferred because cAdvisor cannot see sibling containers on
     * Docker Desktop / WSL2 (the overlayfs layer database it needs is absent), which silently leaves
     * only the container-root series. cAdvisor remains the fallback for hosts where it does work.
     */
    public OptionalDouble containerCpuCores(String container, Instant start, Instant end) {
        OptionalDouble jvm = rangeAverage(
                "process_cpu_usage{application=\"" + container + "\"}", start, end, 2);
        if (jvm.isPresent()) {
            return jvm;
        }
        String query = "sum(rate(container_cpu_usage_seconds_total{name=\"" + container + "\"}[30s]))";
        return rangeAverage(query, start, end, 2);
    }

    /** Average working-set memory of a backend in MB over {@code [start, end]}, or empty. */
    public OptionalDouble containerMemoryMb(String container, Instant start, Instant end) {
        OptionalDouble jvm = rangeAverage(
                "sum(jvm_memory_used_bytes{application=\"" + container + "\"}) / " + BYTES_PER_MB,
                start, end, 2);
        if (jvm.isPresent()) {
            return jvm;
        }
        String query = "sum(container_memory_working_set_bytes{name=\"" + container + "\"}) / " + BYTES_PER_MB;
        return rangeAverage(query, start, end, 2);
    }

    private JsonNode query(String path, Map<String, String> params) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v1/" + path);
            params.forEach(builder::queryParam);
            // Encode explicitly: PromQL contains characters that are illegal in a raw URI.
            URI uri = builder.build().encode().toUri();
            JsonNode response = prometheusRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !"success".equals(response.path("status").asText())) {
                return null;
            }
            return response.path("data");
        } catch (Exception e) {
            log.debug("Prometheus query failed ({}): {}", path, e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        OptionalDouble up = instantScalar("up");
        return up.isPresent();
    }

    /** Null-safe conversion for JSON output. */
    public static Double orNull(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }
}
