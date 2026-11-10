package com.benchmark.runner.service;

import com.benchmark.runner.config.RunnerProperties;
import com.benchmark.runner.exec.K6Runner;
import com.benchmark.runner.exec.ShellScriptRunner;
import com.benchmark.runner.metrics.PrometheusClient;
import com.benchmark.runner.model.ExperimentDtos.ExperimentRequest;
import com.benchmark.runner.model.ExperimentDtos.ExperimentRun;
import com.benchmark.runner.model.ExperimentDtos.ExplainSnapshot;
import com.benchmark.runner.model.ExperimentDtos.RunProgress;
import com.benchmark.runner.model.ExperimentDtos.RunStatus;
import com.benchmark.runner.model.ExperimentDtos.SampleMetrics;
import com.benchmark.runner.model.ExperimentDtos.TargetRunResult;
import com.benchmark.runner.scenario.ScenarioCatalog;
import com.benchmark.runner.scenario.TargetCatalog;
import com.benchmark.runner.scenario.TargetCatalog.ResolvedTarget;
import com.benchmark.runner.scenario.TargetLabels;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Orchestrates one experiment: for every target it applies the implied admin configuration, runs a
 * warm-up that is excluded from the results, then {@code repetitions} measured k6 runs, and finally
 * aggregates and persists them.
 * <p>
 * Targets are measured strictly one after another and only one experiment may run at a time: two
 * load generators competing for the same CPU would invalidate every comparison (spec section 10.1).
 */
@Service
public class ExperimentService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);
    private static final int MAX_LOG_LINES = 5000;
    /** How long a target may take to report UP before it is measured anyway. */
    private static final int READINESS_TIMEOUT_SEC = 60;

    private final RunnerProperties properties;
    private final ScenarioCatalog scenarioCatalog;
    private final TargetCatalog targetCatalog;
    private final TargetSetupService targetSetupService;
    private final ExplainProbeService explainProbeService;
    private final K6Runner k6Runner;
    private final ShellScriptRunner shellScriptRunner;
    private final PrometheusClient prometheusClient;
    private final TimelineCollector timelineCollector;
    private final ResultAggregator aggregator;
    private final ResultWriter resultWriter;
    private final RunRegistry runRegistry;
    private final RestClient serviceRestClient;

    private final AtomicBoolean experimentRunning = new AtomicBoolean(false);

    public ExperimentService(RunnerProperties properties,
                             ScenarioCatalog scenarioCatalog,
                             TargetCatalog targetCatalog,
                             TargetSetupService targetSetupService,
                             ExplainProbeService explainProbeService,
                             K6Runner k6Runner,
                             ShellScriptRunner shellScriptRunner,
                             PrometheusClient prometheusClient,
                             TimelineCollector timelineCollector,
                             ResultAggregator aggregator,
                             ResultWriter resultWriter,
                             RunRegistry runRegistry,
                             @Qualifier("serviceRestClient") RestClient serviceRestClient) {
        this.properties = properties;
        this.scenarioCatalog = scenarioCatalog;
        this.targetCatalog = targetCatalog;
        this.targetSetupService = targetSetupService;
        this.explainProbeService = explainProbeService;
        this.k6Runner = k6Runner;
        this.shellScriptRunner = shellScriptRunner;
        this.prometheusClient = prometheusClient;
        this.timelineCollector = timelineCollector;
        this.aggregator = aggregator;
        this.resultWriter = resultWriter;
        this.runRegistry = runRegistry;
        this.serviceRestClient = serviceRestClient;
    }

    public ExperimentRun start(ExperimentRequest request) {
        ExperimentRequest normalized = normalize(request);
        Path script = scenarioCatalog.resolveScript(normalized.getScenario())
                .orElseThrow(() -> new IllegalArgumentException("Nema k6 skripte za scenario "
                        + normalized.getScenario() + " u direktoriju " + properties.loadtestsDir()));
        // Resolve every target up front so an unknown name fails the request instead of the run.
        normalized.getTargets().forEach(targetCatalog::resolve);

        if (!experimentRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Drugi eksperiment je već u toku; integritet mjerenja "
                    + "zahtijeva da se eksperimenti pokreću jedan po jedan.");
        }

        ExperimentRun run = ExperimentRun.builder()
                .id(UUID.randomUUID().toString())
                .status(RunStatus.PENDING)
                .request(normalized)
                .scenarioTitle(ScenarioCatalog.title(normalized.getScenario()))
                .script(script.toString())
                .startedAt(Instant.now().toString())
                .progress(RunProgress.builder()
                        .completedSteps(0)
                        .totalSteps(totalSteps(normalized))
                        .currentStep("na čekanju")
                        .build())
                .targets(Collections.synchronizedList(new ArrayList<>()))
                .log(Collections.synchronizedList(new ArrayList<>()))
                .build();
        runRegistry.register(run);

        Thread.ofVirtual().name("experiment-" + run.getId()).start(() -> {
            try {
                execute(run);
            } finally {
                experimentRunning.set(false);
            }
        });
        return run;
    }

    private void execute(ExperimentRun run) {
        run.setStatus(RunStatus.RUNNING);
        Path workDir;
        try {
            workDir = Files.createTempDirectory("k6-" + run.getId());
        } catch (IOException e) {
            finish(run, RunStatus.FAILED, "Ne mogu da napravim radni direktorij za k6: " + e.getMessage());
            return;
        }

        int completed = 0;
        try {
            resetDataset(run);
            for (String targetName : run.getRequest().getTargets()) {
                TargetRunResult result = measure(run, workDir, targetName,
                        completed, run.getRequest().getRepetitions());
                run.getTargets().add(result);
                completed += run.getRequest().getRepetitions()
                        + (run.getRequest().getWarmupSec() > 0 ? 1 : 0);
                run.setProgress(RunProgress.builder()
                        .completedSteps(completed)
                        .totalSteps(totalSteps(run.getRequest()))
                        .currentStep("završeno: " + TargetLabels.target(targetName))
                        .build());
            }
            boolean anySample = run.getTargets().stream().anyMatch(t -> !t.getSamples().isEmpty());
            finish(run, anySample ? RunStatus.COMPLETED : RunStatus.FAILED,
                    anySample ? null : "Nijedan sistem nije dao mjerenje; pogledaj log pokretanja.");
        } catch (RuntimeException e) {
            log.error("Experiment {} failed", run.getId(), e);
            finish(run, RunStatus.FAILED, e.getMessage());
        } finally {
            deleteRecursively(workDir);
        }
    }

    private TargetRunResult measure(ExperimentRun run, Path workDir, String targetName,
                                    int completedSteps, int repetitions) {
        ResolvedTarget target = targetCatalog.resolve(targetName);
        String baseUrl = target.definition().getBaseUrl();
        String container = target.definition().getContainer();
        String scenario = run.getRequest().getScenario();
        // Serbian display name, so the progress and the log are readable in the console.
        String label = TargetLabels.target(targetName);
        // A target may pin its own k6 script; otherwise the scenario default is used.
        Path script = targetScript(target, scenario);

        TargetRunResult.TargetRunResultBuilder builder = TargetRunResult.builder()
                .target(target.requestedName())
                .resolvedTarget(target.resolvedName())
                .baseUrl(baseUrl)
                .container(container)
                .variant(target.variant())
                .writeConcern(target.writeConcern())
                .script(script.toString())
                .setup(target.setup());

        log(run, "[runner] sistem " + label + ": " + baseUrl
                + (target.setup().isEmpty() ? "" : " " + target.setup()));
        targetSetupService.apply(target, line -> log(run, line));

        String normalized = ScenarioCatalog.normalize(scenario);
        boolean capturesExplain = false; // No indexing scenarios remain
        // Capture the planner output under the configuration that is about to be measured, so the
        // stored result proves whether the toggled indexes were actually usable (S4/S4B).
        List<ExplainSnapshot> explainPlans = capturesExplain
                ? explainProbeService.capture(baseUrl, normalized, line -> log(run, line))
                : List.of();

        waitUntilReady(baseUrl, label, run);

        int warmupSec = run.getRequest().getWarmupSec();
        if (warmupSec > 0) {
            log(run, "[runner] zagrijavanje za " + label + " (" + warmupSec + "s, ne ulazi u rezultat)");
            K6Runner.Outcome warmup = k6Runner.run(script, baseUrl, scenario,
                    run.getRequest().getClients(), warmupSec, run.getRequest().getDatasetSize(),
                    workDir, 0, line -> log(run, line));
            if (!warmup.isSuccess()) {
                log(run, "[runner] zagrijavanje nije uspjelo: " + warmup.error());
            }
        }

        boolean resilienceRun = target.definition().getInjectScript() != null
                || false; // S6 removed
        List<SampleMetrics> samples = new ArrayList<>();
        String error = null;

        try {
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                run.setProgress(RunProgress.builder()
                        .completedSteps(completedSteps)
                        .totalSteps(totalSteps(run.getRequest()))
                        .currentStep(label + " — repeticija " + repetition + "/" + repetitions)
                        .build());

                waitUntilReady(baseUrl, label, run);
                Future injection = scheduleInjection(target, line -> log(run, line), run);
                K6Runner.Outcome outcome = k6Runner.run(script, baseUrl, scenario,
                        run.getRequest().getClients(), run.getRequest().getDurationSec(),
                        run.getRequest().getDatasetSize(), workDir, repetition, line -> log(run, line));
                injection.join();
                runRecovery(target, line -> log(run, line));

                if (!outcome.isSuccess()) {
                    error = outcome.error();
                    log(run, "[runner] " + label + ": mjerenje prekinuto: " + error);
                    break;
                }
                SampleMetrics sample = withResourceMetrics(outcome.metrics(), container, resilienceRun);
                samples.add(sample);
                completedSteps++;
                log(run, String.format("[runner] %s, repeticija %d: rps=%.1f, p95=%.1f ms, p99=%.1f ms, greške=%.3f%%",
                        label, repetition, sample.getRps(), sample.getLatencyP95(), sample.getLatencyP99(),
                        sample.getErrorRate() * 100));
            }
        } finally {
            // no per-scenario teardown needed
        }

        return builder
                .explainPlans(explainPlans)
                .samples(samples)
                .aggregated(aggregator.aggregate(samples))
                .error(error)
                .build();
    }

    /**
     * Picks the k6 script for a target: the target's explicit override when present, otherwise the
     * first script in the scenario folder. Resolved per target so that one scenario can drive
     * genuinely different workloads (S6: steady resilience load versus a concurrency probe).
     */
    private Path targetScript(ResolvedTarget target, String scenario) {
        String override = target.definition().getScript();
        if (override != null && !override.isBlank()) {
            return scenarioCatalog.resolveScriptPath(override);
        }
        return scenarioCatalog.resolveScript(scenario)
                .orElseThrow(() -> new IllegalArgumentException("Nema k6 skripte za scenario "
                        + scenario + " u direktoriju " + properties.loadtestsDir()));
    }

    private SampleMetrics withResourceMetrics(SampleMetrics sample, String container, boolean resilienceRun) {
        Instant start = Instant.parse(sample.getStartedAt());
        Instant end = Instant.parse(sample.getFinishedAt());
        sample.setCpuAvg(PrometheusClient.orNull(prometheusClient.containerCpuCores(container, start, end)));
        sample.setMemAvg(PrometheusClient.orNull(prometheusClient.containerMemoryMb(container, start, end)));
        if (resilienceRun) {
            sample.setTimeline(timelineCollector.collect(container, start, end));
        }
        return sample;
    }

    /**
     * Waits until the target reports {@code UP} on its actuator health endpoint before a measurement
     * starts.
     * <p>
     * A failure-injection target brings its container back before the application can use it again:
     * after {@code start-redis.sh} answers PING the service's own Redis client still needs a moment
     * to reconnect, and a measurement started in that window records errors that belong to the
     * recovery rather than to the workload under test (S6's concurrency probe runs right after the
     * redis-down target and was hit by exactly that). The health endpoint is used because it reports
     * the service's own view of its dependencies, which is what the measurement depends on.
     * <p>
     * The wait is bounded and never fatal: a target that stays unhealthy is measured anyway so a
     * genuine outage remains visible instead of turning the whole run into an error.
     */
    private void waitUntilReady(String baseUrl, String targetLabel, ExperimentRun run) {
        long deadline = System.nanoTime() + READINESS_TIMEOUT_SEC * 1_000_000_000L;
        int attempts = 0;
        while (true) {
            attempts++;
            String status = probeHealth(baseUrl);
            if ("UP".equals(status)) {
                if (attempts > 1) {
                    log(run, "[runner] " + targetLabel + " je ponovo prijavio stanje UP nakon "
                            + (attempts - 1) + "s");
                }
                return;
            }
            if (System.nanoTime() >= deadline) {
                log(run, "[runner] " + targetLabel + " nije prijavio stanje UP u roku od "
                        + READINESS_TIMEOUT_SEC + "s (zadnje stanje: " + status + "); mjeri se ipak");
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Reads {@code /actuator/health}; a down service answers 503, which is treated as not ready. */
    private String probeHealth(String baseUrl) {
        try {
            JsonNode body = serviceRestClient.get()
                    .uri(baseUrl + "/actuator/health")
                    .retrieve()
                    .body(JsonNode.class);
            return body == null ? "UNKNOWN" : body.path("status").asText("UNKNOWN");
        } catch (RestClientResponseException e) {
            return "HTTP " + e.getStatusCode().value();
        } catch (Exception e) {
            return "DOWN";
        }
    }

    /** Fires the failure-injection script once, mid-test, without blocking the load generator. */
    private Future scheduleInjection(ResolvedTarget target, Consumer<String> sink, ExperimentRun run) {
        String script = target.definition().getInjectScript();
        if (script == null) {
            return Future.NONE;
        }
        int delaySec = target.definition().getInjectDelaySec();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delaySec * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            log(run, "[runner] ubacujem otkaz nakon " + delaySec + "s: " + script);
            shellScriptRunner.run(script, sink);
        });
        return new Future(thread);
    }

    private void runRecovery(ResolvedTarget target, Consumer<String> sink) {
        String script = target.definition().getRecoverScript();
        if (script != null) {
            shellScriptRunner.run(script, sink);
        }
    }

    /**
     * Restores the canonical dataset before the first measurement of an experiment.
     * <p>
     * Write-heavy scenarios (S3 bulk insert) grow the catalog for the duration of the run. The
     * catalog services then filter over that grown catalog, and MongoDB has no secondary index for
     * the catalog filters, so every read becomes a full collection scan. Left alone the collection
     * keeps growing across runs until the scans exceed the k6 request timeout and every read in a
     * later S1 run fails as {@code request timeout}. Re-seeding before each experiment keeps every
     * comparison against the same dataset, which is what the benchmark integrity rules require.
     * <p>
     * The reset is not fatal: a failure is logged and the run continues so a broken seeder cannot
     * silently turn into a "passed" measurement.
     */
    private void resetDataset(ExperimentRun run) {
        log(run, "[runner] vraćam kanonski dataset prije mjerenja");
        ShellScriptRunner.Outcome outcome = shellScriptRunner.run("reset-dataset.sh",
                line -> log(run, line), String.valueOf(run.getRequest().getDatasetSize()));
        if (!outcome.success()) {
            log(run, "[runner] reset dataseta nije uspio (izlazni kod " + outcome.exitCode() + "): "
                    + outcome.error() + "; mjeri se postojeći dataset");
        }
    }

    private void finish(ExperimentRun run, RunStatus status, String error) {
        run.setStatus(status);
        run.setError(error);
        run.setFinishedAt(Instant.now().toString());
        run.setProgress(RunProgress.builder()
                .completedSteps(run.getProgress().getTotalSteps())
                .totalSteps(run.getProgress().getTotalSteps())
                .currentStep(TargetLabels.statusStep(status))
                .build());
        try {
            run.setResultFile(resultWriter.write(run).toString());
        } catch (IOException e) {
            log.error("Cannot write result for run {}", run.getId(), e);
            log(run, "[runner] nije uspjelo upisivanje datoteke rezultata: " + e.getMessage());
        }
        log(run, "[runner] mjerenje " + TargetLabels.statusStep(status)
                + (error == null ? "" : ": " + error));
    }

    private void log(ExperimentRun run, String line) {
        List<String> lines = run.getLog();
        synchronized (lines) {
            lines.add(line);
            while (lines.size() > MAX_LOG_LINES) {
                lines.remove(0);
            }
        }
    }

    private int totalSteps(ExperimentRequest request) {
        int perTarget = request.getRepetitions() + (request.getWarmupSec() > 0 ? 1 : 0);
        return Math.max(1, request.getTargets().size() * perTarget);
    }

    private ExperimentRequest normalize(ExperimentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Zahtjev mora imati tijelo (request body)");
        }
        if (!ScenarioCatalog.isKnownScenario(request.getScenario())) {
            throw new IllegalArgumentException("Nepoznat scenario '" + request.getScenario()
                    + "'. Poznati scenariji: " + ScenarioCatalog.knownScenarios());
        }
        request.setScenario(ScenarioCatalog.normalize(request.getScenario()));
        requirePositive(request.getClients(), "broj klijenata");
        requirePositive(request.getDurationSec(), "trajanje (s)");
        requirePositive(request.getDatasetSize(), "veličina dataseta");
        if (request.getTargets() == null || request.getTargets().isEmpty()) {
            throw new IllegalArgumentException("Potreban je najmanje jedan sistem");
        }
        if (request.getRepetitions() == null) {
            request.setRepetitions(properties.getDefaults().getRepetitions());
        }
        if (request.getRepetitions() < 1) {
            throw new IllegalArgumentException("Broj repeticija mora biti najmanje 1");
        }
        if (request.getWarmupSec() == null) {
            request.setWarmupSec(properties.getDefaults().getWarmupSec());
        }
        if (request.getWarmupSec() < 0) {
            throw new IllegalArgumentException("Trajanje zagrijavanja ne može biti negativno");
        }
        return request;
    }

    private void requirePositive(Integer value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " mora biti pozitivan broj");
        }
    }

    private void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Cannot delete {}", path);
                }
            });
        } catch (IOException e) {
            log.debug("Cannot clean up {}", directory);
        }
    }

    /** Handle to a fire-and-forget injection thread. */
    private record Future(Thread thread) {
        static final Future NONE = new Future(null);

        void join() {
            if (thread == null) {
                return;
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
