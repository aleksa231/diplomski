package com.benchmark.runner.service;

import com.benchmark.runner.config.RunnerProperties;
import com.benchmark.runner.metrics.PrometheusClient;
import com.benchmark.runner.model.LiveDtos.LiveStatus;
import com.benchmark.runner.model.LiveDtos.ServiceHealth;
import com.benchmark.runner.model.LiveDtos.ServiceMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Backs {@code GET /api/live}: current rps, p95, cache hit ratio and service health (spec section
 * 10.2). Health comes from the services themselves so a down service is reported as DOWN even when
 * Prometheus has stale samples for it.
 */
@Service
public class LiveStatusService {

    private static final Logger log = LoggerFactory.getLogger(LiveStatusService.class);
    private static final String RPS_QUERY =
            "sum by (application) (rate(http_server_requests_seconds_count[1m]))";
    private static final String P95_QUERY =
            "histogram_quantile(0.95, sum by (le, application) (rate(http_server_requests_seconds_bucket[1m]))) * 1000";
    private static final String CACHE_HIT_RATIO_QUERY = "sum by (application) (cache_hit_ratio)";
    private static final String CPU_JVM_QUERY = "sum by (application) (process_cpu_usage)";
    private static final String CPU_CADVISOR_QUERY = "sum by (name) (rate(container_cpu_usage_seconds_total[1m]))";
    private static final String MEMORY_JVM_QUERY = "sum by (application) (jvm_memory_used_bytes) / 1048576";
    private static final String MEMORY_CADVISOR_QUERY = "sum by (name) (container_memory_working_set_bytes) / 1048576";

    private final RunnerProperties properties;
    private final PrometheusClient prometheus;
    private final RestClient serviceRestClient;

    public LiveStatusService(RunnerProperties properties, PrometheusClient prometheus,
                             RestClient serviceRestClient) {
        this.properties = properties;
        this.prometheus = prometheus;
        this.serviceRestClient = serviceRestClient;
    }

    public LiveStatus current() {
        boolean prometheusAvailable = prometheus.isAvailable();

        Map<String, Double> rps = prometheusAvailable ? prometheus.instantVector(RPS_QUERY, "application") : Map.of();
        Map<String, Double> p95 = prometheusAvailable ? prometheus.instantVector(P95_QUERY, "application") : Map.of();
        Map<String, Double> cacheHitRatio =
                prometheusAvailable ? prometheus.instantVector(CACHE_HIT_RATIO_QUERY, "application") : Map.of();
        
        Map<String, Double> cpu = new java.util.HashMap<>();
        Map<String, Double> memory = new java.util.HashMap<>();
        
        if (prometheusAvailable) {
            // JVM metrics are preferred because they are available even when cAdvisor is down
            cpu.putAll(prometheus.instantVector(CPU_JVM_QUERY, "application"));
            memory.putAll(prometheus.instantVector(MEMORY_JVM_QUERY, "application"));
            
            // cAdvisor fallbacks for containers that don't expose JVM metrics (e.g. redis/mongo) 
            // or where JVM metrics are missing
            Map<String, Double> cpuFallback = prometheus.instantVector(CPU_CADVISOR_QUERY, "name");
            Map<String, Double> memFallback = prometheus.instantVector(MEMORY_CADVISOR_QUERY, "name");
            
            cpuFallback.forEach(cpu::putIfAbsent);
            memFallback.forEach(memory::putIfAbsent);
        }

        List<ServiceHealth> services = new ArrayList<>();
        List<ServiceMetrics> metrics = new ArrayList<>();
        for (RunnerProperties.ServiceDefinition service : properties.getServices()) {
            services.add(health(service));
            metrics.add(ServiceMetrics.builder()
                    .application(service.getName())
                    .rps(rps.get(service.getName()))
                    .p95Ms(p95.get(service.getName()))
                    .cacheHitRatio(cacheHitRatio.get(service.getName()))
                    .cpuCores(cpu.get(service.getName()))
                    .memoryMb(memory.get(service.getName()))
                    .build());
        }
        return LiveStatus.builder()
                .timestamp(Instant.now().toString())
                .prometheusAvailable(prometheusAvailable)
                .services(services)
                .metrics(metrics)
                .build();
    }

    private ServiceHealth health(RunnerProperties.ServiceDefinition service) {
        try {
            JsonNode body = serviceRestClient.get()
                    .uri(service.getUrl() + "/actuator/health")
                    .retrieve()
                    .body(JsonNode.class);
            String status = body == null ? "UNKNOWN" : body.path("status").asText("UNKNOWN");
            return ServiceHealth.builder()
                    .name(service.getName())
                    .url(service.getUrl())
                    .status("UP".equals(status) ? "UP" : "DOWN")
                    .detail(status)
                    .build();
        } catch (Exception e) {
            log.debug("Health probe failed for {}: {}", service.getName(), e.getMessage());
            return ServiceHealth.builder()
                    .name(service.getName())
                    .url(service.getUrl())
                    .status("DOWN")
                    .detail(e.getMessage())
                    .build();
        }
    }
}
