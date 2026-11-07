package com.benchmark.runner.scenario;

import com.benchmark.runner.config.RunnerProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the target names used by the Makefile and the console into a concrete backend.
 * <p>
 * Names configured explicitly under {@code app.targets} are used as-is. Any other name is derived
 * from keywords so that the scenario targets stay declarative:
 * {@code mongo-w1} -> {@code nosql-blocking} with write concern {@code w1},
 * {@code postgres-unindexed} -> {@code relational} with indexes dropped, and so on. Whatever is
 * derived is recorded next to the result, because benchmark honesty requires knowing the exact
 * configuration a number was produced under (spec section 10.1).
 */
@Component
public class TargetCatalog {

    private final RunnerProperties properties;

    public TargetCatalog(RunnerProperties properties) {
        this.properties = properties;
    }

    /**
     * @param requestedName  target name as sent by the client
     * @param resolvedName   configured target it maps to
     * @param definition     base URL / container / failure-injection script
     * @param setup          admin calls to apply before the load, for the result metadata
     * @param variant        relational variant label, when the name implies one
     * @param writeConcern   Mongo write concern label, when the name implies one
     * @param cacheEnabled   desired catalog-cache state, {@code null} to leave untouched
     * @param indexesEnabled desired experiment-index state, {@code null} to leave untouched
     */
    public record ResolvedTarget(
            String requestedName,
            String resolvedName,
            RunnerProperties.TargetDefinition definition,
            List<String> setup,
            String variant,
            String writeConcern,
            Boolean cacheEnabled,
            Boolean indexesEnabled) {
    }

    public ResolvedTarget resolve(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        String key = name.toLowerCase(Locale.ROOT);
        RunnerProperties.TargetDefinition definition = findConfigured(key)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown target '" + name + "'. Configured targets: " + properties.getTargets().keySet()
                                + "; derived names may be based on " + properties.getTargets().keySet()
                                + " (e.g. mongo-unindexed, postgres-unindexed, relational-jsonb)."));

        Boolean cache = cacheState(key);
        Boolean indexes = indexState(key);
        String variant = key.contains("jsonb") ? "jsonb" : (key.contains("normalized") ? "normalized" : null);
        String writeConcern = key.contains("w1") ? "w1" : (key.contains("majority") ? "majority" : null);

        List<String> setup = new ArrayList<>();
        if (cache != null) {
            setup.add("cache=" + (cache ? "enabled" : "disabled"));
        }
        if (indexes != null) {
            setup.add("indexes=" + (indexes ? "enabled" : "disabled"));
        }
        if (variant != null) {
            setup.add("variant=" + variant);
        }
        if (writeConcern != null) {
            setup.add("writeConcern=" + writeConcern);
        }
        return new ResolvedTarget(name, definition == null ? name : resolvedName(key, definition),
                definition, setup, variant, writeConcern, cache, indexes);
    }

    private String resolvedName(String key, RunnerProperties.TargetDefinition definition) {
        for (Map.Entry<String, RunnerProperties.TargetDefinition> entry : properties.getTargets().entrySet()) {
            if (entry.getValue() == definition) {
                return entry.getKey();
            }
        }
        return key;
    }

    private Optional<RunnerProperties.TargetDefinition> findConfigured(String key) {
        return properties.getTargets().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .or(() -> resolveBase(key));
    }

    private Optional<RunnerProperties.TargetDefinition> resolveBase(String key) {
        if (key.contains("nosql-reactive") || key.contains("reactive")) {
            return configured("nosql-reactive");
        }
        if (key.contains("mongo") || key.contains("nosql")) {
            // The w1 write concern is applied to the Mongo client at startup, so it needs its own
            // process; anything else (mongo-majority, mongo-indexed, ...) uses the base backend.
            return key.contains("w1") ? configured("nosql-blocking-w1") : configured("nosql-blocking");
        }
        if (key.contains("postgres") || key.contains("relational")) {
            // The relational storage model is fixed at startup as well: jsonb is a second process.
            return key.contains("jsonb") ? configured("relational-jsonb") : configured("relational");
        }
        return Optional.empty();
    }

    private Optional<RunnerProperties.TargetDefinition> configured(String name) {
        return properties.getTargets().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private Boolean cacheState(String key) {
        if (key.contains("no-cache")) {
            return Boolean.FALSE;
        }
        return key.contains("cache") ? Boolean.TRUE : null;
    }

    private Boolean indexState(String key) {
        // "unindexed" is tested first because it also contains "indexed".
        if (key.contains("unindexed")) {
            return Boolean.FALSE;
        }
        return key.contains("indexed") ? Boolean.TRUE : null;
    }
}
