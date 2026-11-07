package com.benchmark.runner.controller;

import com.benchmark.runner.model.ExperimentDtos.ExperimentRequest;
import com.benchmark.runner.model.ExperimentDtos.ExperimentRun;
import com.benchmark.runner.service.ExperimentService;
import com.benchmark.runner.service.ResultWriter;
import com.benchmark.runner.service.RunRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@code POST /api/experiments}, {@code GET /api/experiments} and {@code GET /api/experiments/{id}}
 * (spec section 10.2).
 */
@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final RunRegistry runRegistry;
    private final ResultWriter resultWriter;

    public ExperimentController(ExperimentService experimentService, RunRegistry runRegistry,
                                ResultWriter resultWriter) {
        this.experimentService = experimentService;
        this.runRegistry = runRegistry;
        this.resultWriter = resultWriter;
    }

    /** Starts a run. The load itself happens asynchronously; poll GET /{id} for progress. */
    @PostMapping
    public ResponseEntity<ExperimentRun> start(@RequestBody ExperimentRequest request) {
        return ResponseEntity.accepted().body(experimentService.start(request));
    }

    @GetMapping
    public List<ExperimentRun> list() {
        return runRegistry.summaries();
    }

    @GetMapping("/{id}")
    public ExperimentRun get(@PathVariable String id) {
        return runRegistry.find(id).orElseThrow(() -> new NoSuchElementException(unknownRun(id)));
    }

    /** The persisted result document, exactly as written into {@code results/}. */
    @GetMapping(value = "/{id}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public String result(@PathVariable String id) throws IOException {
        ExperimentRun run = runRegistry.find(id)
                .orElseThrow(() -> new NoSuchElementException(unknownRun(id)));
        if (run.getResultFile() == null) {
            throw new NoSuchElementException("Run " + id + " has no result file yet (status "
                    + run.getStatus() + ")");
        }
        return resultWriter.read(Path.of(run.getResultFile()));
    }

    /**
     * Finished runs are re-loaded from {@code results/} at startup, so an unknown id means either a
     * typo or a run that was still in flight when the runner restarted and never got persisted.
     */
    private String unknownRun(String id) {
        return "No experiment run with id " + id + ". Finished runs are re-loaded from results/ at "
                + "startup; a run that was still in flight when the runner restarted was never "
                + "persisted and cannot be resumed.";
    }
}
