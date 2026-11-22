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
 */
@Component
public class TargetCatalog {

    private final RunnerProperties properties;

    public TargetCatalog(RunnerProperties properties) {
        this.properties = properties;
    }

    public record ResolvedTarget(
            String requestedName,
            String resolvedName,
            RunnerProperties.TargetDefinition definition,
            List<String> setup,
            String variant,
            Boolean cacheEnabled,
            Boolean indexesEnabled) {
    }

    public ResolvedTarget resolve(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        String key = name.toLowerCase(Locale.ROOT);
        RunnerProperties.TargetDefinition definition = findConfigured(key)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nepoznat sistem '" + name + "'. Konfigurisani sistemi: " + properties.getTargets().keySet()));

        Boolean cache = cacheState(key);
        Boolean indexes = indexState(key);
        String variant = key.contains("normalized") ? "normalized" : null;

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
        return new ResolvedTarget(name, definition == null ? name : resolvedName(key, definition),
                definition, setup, variant, cache, indexes);
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
            return configured("nosql-blocking");
        }
        if (key.contains("postgres") || key.contains("relational")) {
            return configured("relational");
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
        if (key.contains("unindexed")) {
            return Boolean.FALSE;
        }
        return key.contains("indexed") ? Boolean.TRUE : null;
    }
}
