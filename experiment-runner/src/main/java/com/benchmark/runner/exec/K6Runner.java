package com.benchmark.runner.exec;

import com.benchmark.runner.config.RunnerProperties;
import com.benchmark.runner.model.ExperimentDtos.SampleMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs a k6 script as a subprocess (the k6 binary is bundled in the runner image) and turns the
 * end-of-test summary into {@link SampleMetrics}.
 * <p>
 * The script receives the same environment variables the standalone {@code make scenario-*} targets
 * use, so a run through the runner and a run through the Makefile measure the same thing.
 */
@Component
public class K6Runner {

    private static final Logger log = LoggerFactory.getLogger(K6Runner.class);

    /** p(50) and p(99) are not in the k6 defaults, so they are requested explicitly. */
    private static final String TREND_STATS = "avg,min,med,p(50),p(90),p(95),p(99),max";

    private final RunnerProperties properties;
    private final ObjectMapper objectMapper;

    public K6Runner(RunnerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record Outcome(SampleMetrics metrics, String error) {
        public boolean isSuccess() {
            return error == null;
        }
    }

    /**
     * Executes one k6 invocation.
     *
     * @param script       k6 script to run
     * @param baseUrl      value for {@code TARGET_URL}
     * @param scenario     scenario tag, e.g. {@code S1}
     * @param clients      virtual users
     * @param durationSec  test duration
     * @param datasetSize  id-pool size hint for the script
     * @param workDir      directory that receives the summary export
     * @param repetition   1-based repetition number, recorded in the result
     * @param logSink      receives k6's stdout/stderr line by line
     */
    public Outcome run(Path script, String baseUrl, String scenario, int clients, int durationSec,
                       int datasetSize, Path workDir, int repetition, Consumer<String> logSink) {
        Path summaryFile = workDir.resolve("k6-summary-" + repetition + ".json");
        List<String> command = buildCommand(script, baseUrl, scenario, clients, durationSec, datasetSize, summaryFile);
        log.info("k6 run: {}", String.join(" ", command));

        Instant startedAt = Instant.now();
        int exitCode;
        try {
            exitCode = execute(command, workDir, logSink);
        } catch (IOException e) {
            return new Outcome(null, "Nije uspjelo pokretanje k6: " + e.getMessage());
        }
        Instant finishedAt = Instant.now();

        if (!Files.isRegularFile(summaryFile)) {
            return new Outcome(null, "k6 nije proizveo izvoz rezimea (izlazni kod " + exitCode
                    + "). Pogledaj log pokretanja.");
        }
        try {
            SampleMetrics metrics = parseSummary(summaryFile, repetition, startedAt, finishedAt, exitCode);
            if (exitCode != 0) {
                logSink.accept("[runner] k6 završio sa kodom " + exitCode
                        + " (prekoračena granica ne poništava mjerenje, ali se bilježi).");
            }
            return new Outcome(metrics, null);
        } catch (IOException e) {
            return new Outcome(null, "Ne mogu pročitati k6 rezime: " + e.getMessage());
        }
    }

    private List<String> buildCommand(Path script, String baseUrl, String scenario, int clients,
                                      int durationSec, int datasetSize, Path summaryFile) {
        List<String> command = new ArrayList<>();
        command.add(properties.getDefaults().getK6Binary());
        command.add("run");
        command.add("--no-color");
        command.add("--quiet");
        command.add("--summary-trend-stats=" + TREND_STATS);
        // Belt and braces: the CLI flag works for every existing script, the env var lets a script's
        // handleSummary() write the same file on k6 versions where the flag is removed.
        command.add("--summary-export=" + summaryFile);
        addEnv(command, "TARGET_URL", baseUrl);
        addEnv(command, "SCENARIO", scenario);
        addEnv(command, "CLIENTS", String.valueOf(clients));
        addEnv(command, "DURATION_SEC", String.valueOf(durationSec));
        addEnv(command, "DATASET_SIZE", String.valueOf(datasetSize));
        addEnv(command, "SUMMARY_EXPORT", summaryFile.toString());
        command.add(script.toString());
        return command;
    }

    private void addEnv(List<String> command, String key, String value) {
        command.add("-e");
        command.add(key + "=" + value);
    }

    private int execute(List<String> command, Path workDir, Consumer<String> logSink) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();

        // Virtual thread drains the output so a long test cannot fill the OS pipe buffer.
        Thread drainer = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logSink.accept("[k6] " + line);
                }
            } catch (IOException e) {
                logSink.accept("[runner] k6 izlazni tok se zatvorio: " + e.getMessage());
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(properties.getDefaults().getK6TimeoutSec(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new UncheckedIOException(new IOException("Prekinuto čekanje na k6", e));
        }
        if (!finished) {
            logSink.accept("[runner] k6 je prešao " + properties.getDefaults().getK6TimeoutSec() + "s; prekidam ga.");
            process.destroyForcibly();
            try {
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            drainer.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return process.exitValue();
    }

    SampleMetrics parseSummary(Path summaryFile, int repetition, Instant startedAt, Instant finishedAt,
                               int exitCode) throws IOException {
        JsonNode root = objectMapper.readTree(summaryFile.toFile());
        JsonNode metrics = root.path("metrics");
        JsonNode httpReqs = metrics.path("http_reqs");
        JsonNode duration = metrics.path("http_req_duration");
        JsonNode failed = metrics.path("http_req_failed");

        return SampleMetrics.builder()
                .repetition(repetition)
                .rps(doubleValue(field(httpReqs, "rate")))
                .latencyMean(doubleValue(field(duration, "avg")))
                .latencyP50(firstPresent(duration, "p(50)", "med"))
                .latencyP95(firstPresent(duration, "p(95)"))
                .latencyP99(firstPresent(duration, "p(99)"))
                .errorRate(rateValue(failed))
                .httpRequests((long) doubleValue(field(httpReqs, "count")))
                .iterations((long) doubleValue(field(metrics.path("iterations"), "count")))
                .droppedIterations((long) doubleValue(field(metrics.path("dropped_iterations"), "count")))
                .k6ExitCode(exitCode)
                .startedAt(startedAt.toString())
                .finishedAt(finishedAt.toString())
                .build();
    }

    /**
     * k6 v0.56's {@code --summary-export} nests a metric's data under {@code values}, while older
     * exports (and a script's own {@code handleSummary()}) keep it flat on the metric node. Both
     * layouts are supported so the exact k6 version does not silently zero out the measurements.
     */
    private JsonNode field(JsonNode metric, String name) {
        JsonNode values = metric.path("values");
        return values.isObject() ? values.path(name) : metric.path(name);
    }

    /** A rate metric exposes {@code rate} (nested layout) or {@code value} (flat layout). */
    private double rateValue(JsonNode metric) {
        JsonNode values = metric.path("values");
        if (values.isObject()) {
            return doubleValue(values.path("rate"));
        }
        JsonNode value = metric.path("value");
        return value.isNumber() ? value.asDouble() : doubleValue(metric.path("rate"));
    }

    private double firstPresent(JsonNode metric, String... fieldNames) {
        JsonNode values = metric.path("values");
        JsonNode source = values.isObject() ? values : metric;
        for (String field : fieldNames) {
            JsonNode node = source.path(field);
            if (node.isNumber()) {
                return node.asDouble();
            }
        }
        return 0.0;
    }

    private double doubleValue(JsonNode node) {
        return node.isNumber() ? node.asDouble() : 0.0;
    }
}
