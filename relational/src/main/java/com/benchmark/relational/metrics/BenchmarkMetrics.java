package com.benchmark.relational.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Thin helper that records a Micrometer timer for every backend operation, tagged by
 * backend (postgres/jdbc/redis) and operation name.
 */
@Component
public class BenchmarkMetrics {

    private final MeterRegistry registry;

    public BenchmarkMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T time(String backend, String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return action.get();
        } finally {
            sample.stop(Timer.builder("app.backend.operation")
                    .tag("backend", backend)
                    .tag("operation", operation)
                    .register(registry));
        }
    }

    public void timeVoid(String backend, String operation, Runnable action) {
        time(backend, operation, () -> {
            action.run();
            return null;
        });
    }
}
