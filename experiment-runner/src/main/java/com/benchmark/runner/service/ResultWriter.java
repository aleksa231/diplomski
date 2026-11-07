package com.benchmark.runner.service;

import com.benchmark.runner.config.RunnerProperties;
import com.benchmark.runner.model.ExperimentDtos.ExperimentRequest;
import com.benchmark.runner.model.ExperimentDtos.ExperimentRun;
import com.benchmark.runner.model.ExperimentDtos.ResultDocument;
import com.benchmark.runner.model.ExperimentDtos.TargetRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists a finished run into {@code results/} as a single JSON document (spec section 10.2).
 * The document carries the configuration the numbers were produced under, so a result is always
 * self-describing (spec section 10.1).
 */
@Component
public class ResultWriter {

    private static final Logger log = LoggerFactory.getLogger(ResultWriter.class);
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final RunnerProperties properties;
    private final ObjectMapper objectMapper;

    public ResultWriter(RunnerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** @return the path the document was written to. */
    public Path write(ExperimentRun run) throws IOException {
        Path dir = Path.of(properties.resultsDir());
        Files.createDirectories(dir);

        ResultDocument document = ResultDocument.builder()
                .runId(run.getId())
                .scenario(run.getRequest().getScenario())
                .scenarioTitle(run.getScenarioTitle())
                .script(run.getScript())
                .status(run.getStatus() == null ? null : run.getStatus().name())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .generatedAt(Instant.now().toString())
                .configuration(configuration(run.getRequest(), run.getTargets()))
                .targets(run.getTargets())
                .build();

        Path file = dir.resolve(fileName(run));
        objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(file.toFile(), document);
        log.info("Wrote experiment result to {}", file);
        return file;
    }

    /** Reads a previously written document; used to serve results after a restart. */
    public String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** A persisted document together with the file it was read from. */
    public record PersistedResult(Path file, ResultDocument document) {
    }

    /**
     * Reads every result document already present in {@code results/} so the run index can be
     * rebuilt after a restart. Unreadable files are skipped with a warning instead of failing the
     * startup; one corrupt document must not hide all the others.
     */
    public List<PersistedResult> readAll() {
        Path dir = Path.of(properties.resultsDir());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var files = Files.list(dir)) {
            List<PersistedResult> persisted = new ArrayList<>();
            for (Path file : files.sorted().toList()) {
                if (!file.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                readDocument(file).ifPresent(persisted::add);
            }
            return persisted;
        } catch (IOException e) {
            log.warn("Cannot list the results directory {}: {}", dir, e.getMessage());
            return List.of();
        }
    }

    private Optional<PersistedResult> readDocument(Path file) {
        try {
            return Optional.of(new PersistedResult(file,
                    objectMapper.readValue(file.toFile(), ResultDocument.class)));
        } catch (IOException e) {
            log.warn("Skipping unreadable result file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> configuration(ExperimentRequest request, java.util.List<TargetRunResult> targets) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("datasetSize", request.getDatasetSize());
        configuration.put("clients", request.getClients());
        configuration.put("durationSec", request.getDurationSec());
        configuration.put("repetitions", request.getRepetitions());
        configuration.put("warmupSec", request.getWarmupSec());
        configuration.put("targets", request.getTargets());

        Map<String, Object> targetConfiguration = new LinkedHashMap<>();
        for (TargetRunResult target : targets) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("resolvedTarget", target.getResolvedTarget());
            entry.put("baseUrl", target.getBaseUrl());
            entry.put("setup", target.getSetup());
            if (target.getVariant() != null) {
                entry.put("variant", target.getVariant());
            }
            if (target.getWriteConcern() != null) {
                entry.put("writeConcern", target.getWriteConcern());
            }
            targetConfiguration.put(target.getTarget(), entry);
        }
        configuration.put("targetDetails", targetConfiguration);
        return configuration;
    }

    private String fileName(ExperimentRun run) {
        String scenario = run.getRequest().getScenario() == null
                ? "run" : run.getRequest().getScenario().toLowerCase(java.util.Locale.ROOT);
        return scenario + "-" + FILE_TIMESTAMP.format(Instant.now()) + "-" + shortId(run.getId()) + ".json";
    }

    private String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
