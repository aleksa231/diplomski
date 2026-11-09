package com.benchmark.runner.scenario;

import com.benchmark.runner.config.RunnerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Maps a scenario code to its k6 script. The convention is one folder per scenario under
 * {@code loadtests/} ({@code loadtests/s1/}, {@code loadtests/s2/}, ...) holding one or more k6
 * scripts; the first script in natural order is the scenario entry point.
 */
@Component
public class ScenarioCatalog {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCatalog.class);

    /** Scenario titles from spec section 10.3. */
    private static final Map<String, String> TITLES = new LinkedHashMap<>();

    static {
        TITLES.put("S1", "Schema flexibility and the document model");
        TITLES.put("S2", "Caching and low latency");
        TITLES.put("S3", "Write throughput under load");
        TITLES.put("S4", "Reactive vs blocking");
        TITLES.put("S5", "Where the relational model wins");
    }

    private final RunnerProperties properties;

    public ScenarioCatalog(RunnerProperties properties) {
        this.properties = properties;
    }

    public static boolean isKnownScenario(String scenario) {
        return scenario != null && TITLES.containsKey(normalize(scenario));
    }

    public static List<String> knownScenarios() {
        return List.copyOf(TITLES.keySet());
    }

    public static String title(String scenario) {
        return TITLES.getOrDefault(normalize(scenario), "Custom scenario");
    }

    public static String normalize(String scenario) {
        return scenario == null ? "" : scenario.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Resolves the k6 entry point for a scenario, e.g. {@code loadtests/s1/catalog-read.js}.
     *
     * @return the script path, or empty when no script exists yet for that scenario.
     */
    public Optional<Path> resolveScript(String scenario) {
        Path root = Path.of(properties.loadtestsDir());
        if (!Files.isDirectory(root)) {
            log.warn("loadtests directory {} does not exist", root);
            return Optional.empty();
        }
        String folder = normalize(scenario).toLowerCase(java.util.Locale.ROOT);
        Path scenarioDir;
        try (Stream<Path> children = Files.list(root)) {
            scenarioDir = children
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(folder))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + root, e);
        }
        if (scenarioDir == null) {
            return Optional.empty();
        }
        try (Stream<Path> scripts = Files.list(scenarioDir)) {
            return scripts
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".js"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + scenarioDir, e);
        }
    }

    /**
     * Resolves an explicit k6 script referenced by a target definition, e.g.
     * {@code s6/02-last-item-concurrency.js}, relative to the loadtests root.
     *
     * @throws IllegalArgumentException when the path escapes the loadtests directory or is missing
     */
    public Path resolveScriptPath(String relative) {
        Path root = Path.of(properties.loadtestsDir()).toAbsolutePath().normalize();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Script path escapes the loadtests directory: " + relative);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("k6 script not found: " + resolved);
        }
        return resolved;
    }
}
