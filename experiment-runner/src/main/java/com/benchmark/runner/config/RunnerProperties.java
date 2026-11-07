package com.benchmark.runner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Externalised configuration for the runner (spec section 4: configuration is external, never
 * hard-coded). Everything is bound from the {@code app.*} tree in application.yml and therefore
 * overridable through environment variables or Spring profiles.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class RunnerProperties {

    private Prometheus prometheus = new Prometheus();
    private Directory results = new Directory();
    private Directory loadtests = new Directory();
    private Directory scripts = new Directory();
    private Defaults defaults = new Defaults();
    private List<ServiceDefinition> services = List.of();
    private Map<String, TargetDefinition> targets = new LinkedHashMap<>();

    public String resultsDir() {
        return results.getDir();
    }

    public String loadtestsDir() {
        return loadtests.getDir();
    }

    public String scriptsDir() {
        return scripts.getDir();
    }

    @Data
    public static class Prometheus {
        private String url = "http://prometheus:9090";
    }

    @Data
    public static class Directory {
        private String dir;
    }

    @Data
    public static class Defaults {
        private int warmupSec = 30;
        private int repetitions = 3;
        private long k6TimeoutSec = 900;
        private long scriptTimeoutSec = 120;
        private String k6Binary = "k6";
    }

    /** A service probed by {@code GET /api/live}. */
    @Data
    public static class ServiceDefinition {
        private String name;
        private String url;
    }

    /**
     * A benchmark target: where the load is sent, which container to read CPU/memory from, and
     * optionally which failure-injection script to fire while the load is running (S6).
     */
    @Data
    public static class TargetDefinition {
        private String baseUrl;
        private String container;
        /**
         * Optional k6 script to use instead of the scenario default, relative to
         * {@code app.loadtests.dir} (for example {@code s6/02-last-item-concurrency.js}). S6 mixes
         * workloads that cannot share one script: steady resilience traffic (failover, redis-down)
         * versus a focused concurrency probe.
         */
        private String script;
        /** Script name inside {@code app.scripts.dir} to run once, mid-test. */
        private String injectScript;
        /** Seconds to wait after the load started before firing {@link #injectScript}. */
        private int injectDelaySec;
        /** Script name inside {@code app.scripts.dir} to run right after the load finishes. */
        private String recoverScript;
    }
}
