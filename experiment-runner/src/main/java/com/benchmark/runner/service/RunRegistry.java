package com.benchmark.runner.service;

import com.benchmark.runner.model.ExperimentDtos.ExperimentRequest;
import com.benchmark.runner.model.ExperimentDtos.ExperimentRun;
import com.benchmark.runner.model.ExperimentDtos.ResultDocument;
import com.benchmark.runner.model.ExperimentDtos.RunProgress;
import com.benchmark.runner.model.ExperimentDtos.RunStatus;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Index of runs. The authoritative copy of a finished run is the JSON document in {@code results/};
 * this registry serves the console while the stack is up and is rebuilt from those documents on
 * every startup, so a runner restart does not hide results that were already measured.
 */
@Component
public class RunRegistry {

    private static final Logger log = LoggerFactory.getLogger(RunRegistry.class);

    private final Map<String, ExperimentRun> runs = new ConcurrentHashMap<>();
    private final ResultWriter resultWriter;

    public RunRegistry(ResultWriter resultWriter) {
        this.resultWriter = resultWriter;
    }

    /**
     * Rebuilds the index from {@code results/}. A run that was still in flight when the runner
     * restarted never reached {@link ResultWriter} and therefore cannot come back.
     */
    @PostConstruct
    void loadPersistedRuns() {
        List<ResultWriter.PersistedResult> persisted = resultWriter.readAll();
        int loaded = 0;
        for (ResultWriter.PersistedResult entry : persisted) {
            ResultDocument document = entry.document();
            if (document.getRunId() == null || runs.containsKey(document.getRunId())) {
                continue;
            }
            register(toRun(document, entry.file()));
            loaded++;
        }
        if (loaded > 0) {
            log.info("Re-loaded {} finished run(s) from the results directory", loaded);
        }
    }

    public void register(ExperimentRun run) {
        runs.put(run.getId(), run);
    }

    public Optional<ExperimentRun> find(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    /** Newest first, with the bulky log and per-sample detail stripped out. */
    public List<ExperimentRun> summaries() {
        List<ExperimentRun> result = new ArrayList<>();
        for (ExperimentRun run : runs.values()) {
            result.add(run.toBuilder().log(null).targets(null).build());
        }
        result.sort(Comparator.comparing(ExperimentRun::getStartedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /** Rebuilds the console view of a finished run from its persisted document. */
    private ExperimentRun toRun(ResultDocument document, Path file) {
        RunStatus status = status(document.getStatus());
        return ExperimentRun.builder()
                .id(document.getRunId())
                .status(status)
                .request(request(document))
                .scenarioTitle(document.getScenarioTitle())
                .script(document.getScript())
                .startedAt(document.getStartedAt())
                .finishedAt(document.getFinishedAt())
                .progress(RunProgress.builder()
                        .completedSteps(0)
                        .totalSteps(0)
                        .currentStep(com.benchmark.runner.scenario.TargetLabels.statusStep(status))
                        .build())
                .targets(document.getTargets() == null
                        ? new ArrayList<>() : new ArrayList<>(document.getTargets()))
                .log(Collections.synchronizedList(new ArrayList<>()))
                .resultFile(file.toString())
                .build();
    }

    private ExperimentRequest request(ResultDocument document) {
        Map<String, Object> configuration = document.getConfiguration() == null
                ? Map.of() : document.getConfiguration();
        ExperimentRequest request = new ExperimentRequest();
        request.setScenario(document.getScenario());
        request.setDatasetSize(intValue(configuration.get("datasetSize")));
        request.setClients(intValue(configuration.get("clients")));
        request.setDurationSec(intValue(configuration.get("durationSec")));
        request.setRepetitions(intValue(configuration.get("repetitions")));
        request.setWarmupSec(intValue(configuration.get("warmupSec")));
        request.setTargets(stringList(configuration.get("targets")));
        return request;
    }

    private RunStatus status(String name) {
        if (name == null) {
            return RunStatus.COMPLETED;
        }
        try {
            return RunStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown run status '{}' in a persisted result; reporting it as COMPLETED", name);
            return RunStatus.COMPLETED;
        }
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> result.add(String.valueOf(item)));
        }
        return result;
    }
}
