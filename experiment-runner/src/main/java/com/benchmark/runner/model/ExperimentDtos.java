package com.benchmark.runner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire and persistence model for experiments. Field names of {@link SampleMetrics} are fixed by
 * spec section 10.2: {@code rps, latencyMean, latencyP50, latencyP95, latencyP99, errorRate,
 * cpuAvg, memAvg}, plus aggregated mean and standard deviation.
 */
public final class ExperimentDtos {

    private ExperimentDtos() {
    }

    public enum RunStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        REJECTED
    }

    /** Body of {@code POST /api/experiments}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentRequest {
        private String scenario;
        private Integer datasetSize;
        private Integer clients;
        private Integer durationSec;
        private Integer repetitions;
        /** Optional override of {@code app.defaults.warmup-sec}; 0 disables the warm-up. */
        private Integer warmupSec;
        private List<String> targets = new ArrayList<>();
    }

    /** One point of a per-second timeline, recorded for the resilience runs (S6). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelinePoint {
        private int secondOffset;
        private double rps;
        private double serverErrors;
        private double p95Ms;
    }

    /** One recorded repetition for one target. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleMetrics {
        private int repetition;
        private double rps;
        private double latencyMean;
        private double latencyP50;
        private double latencyP95;
        private double latencyP99;
        private double errorRate;
        /** Average CPU cores used by the target container over the measured window. */
        private Double cpuAvg;
        /** Average working-set memory (MB) of the target container over the measured window. */
        private Double memAvg;
        private long httpRequests;
        private long iterations;
        private long droppedIterations;
        private int k6ExitCode;
        private String startedAt;
        private String finishedAt;
        /** Per-second rps / error / p95 series; only populated for resilience runs. */
        @Builder.Default
        private List<TimelinePoint> timeline = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggregateStats {
        private double mean;
        private double stdDev;
    }

    /**
     * Query plan captured from {@code GET /api/admin/explain} while a target is configured for an
     * S4 measurement. It is stored next to the numbers so that a result can prove whether the
     * indexes the target name promised were actually used by the planner, instead of relying on the
     * target name alone.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExplainSnapshot {
        /** Benchmark query, {@code Q1}..{@code Q5}. */
        private String query;
        /**
         * Normalised access path: {@code INDEX}, {@code COLLECTION_SCAN}, {@code SEQUENTIAL_SCAN}
         * or {@code UNKNOWN} (plan could not be read).
         */
        private String scanType;
        /** True when the plan uses at least one index access path. */
        private boolean usedIndex;
        /** Every plan node/stage found in the plan, e.g. {@code IXSCAN}, {@code FETCH}. */
        @Builder.Default
        private List<String> stages = new ArrayList<>();
        /** Raw plan text as returned by the service, kept for the appendix of the thesis. */
        private String plan;
        /** Non-null when the plan could not be captured (e.g. $text without a text index). */
        private String error;
    }

    /** All repetitions for one target plus the aggregate statistics. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetRunResult {
        /** Name as requested in the experiment. */
        private String target;
        /** Configured target the name was resolved to (e.g. {@code mongo-w1} -> nosql-blocking). */
        private String resolvedTarget;
        private String baseUrl;
        private String container;
        /** Recorded configuration discriminator, e.g. {@code normalized} or {@code jsonb}. */
        private String variant;
        /** Recorded configuration discriminator, e.g. {@code w1} or {@code majority}. */
        private String writeConcern;
        private String script;
        /** Runtime prerequisites applied through the admin API before the load started. */
        @Builder.Default
        private List<String> setup = new ArrayList<>();
        /** S4 only: planner output for Q1..Q5 under the configuration that was measured. */
        @Builder.Default
        private List<ExplainSnapshot> explainPlans = new ArrayList<>();
        @Builder.Default
        private List<SampleMetrics> samples = new ArrayList<>();
        @Builder.Default
        private Map<String, AggregateStats> aggregated = new LinkedHashMap<>();
        /** Non-null when the target could not be measured (missing script, k6 failure, ...). */
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunProgress {
        private int completedSteps;
        private int totalSteps;
        private String currentStep;
    }

    /**
     * Live view of a run, exposed by the REST API. Scalar fields are volatile because the run is
     * mutated by the worker thread while HTTP threads read it.
     */
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentRun {
        private String id;
        private volatile RunStatus status;
        private ExperimentRequest request;
        private String scenarioTitle;
        private String script;
        private String startedAt;
        private volatile String finishedAt;
        @Builder.Default
        private volatile RunProgress progress = RunProgress.builder().build();
        @Builder.Default
        private List<TargetRunResult> targets = new ArrayList<>();
        /** Capped tail of the runner/k6 output, for the console progress view. */
        @Builder.Default
        private List<String> log = new ArrayList<>();
        private volatile String error;
        private volatile String resultFile;
    }

    /** Exactly what is written into {@code results/}. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultDocument {
        private String runId;
        private String scenario;
        private String scenarioTitle;
        private String script;
        private String status;
        private String startedAt;
        private String finishedAt;
        private String generatedAt;
        @Builder.Default
        private Map<String, Object> configuration = new LinkedHashMap<>();
        @Builder.Default
        private List<TargetRunResult> targets = new ArrayList<>();
    }
}
