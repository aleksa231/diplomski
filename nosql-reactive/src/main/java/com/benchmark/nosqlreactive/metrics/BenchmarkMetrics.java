package com.benchmark.nosqlreactive.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive counterpart of the blocking {@code BenchmarkMetrics} helper. Records the same
 * Micrometer timer ({@code app.backend.operation}, tagged by backend and operation) but
 * times the subscription of a {@link Mono}/{@link Flux} instead of a blocking call: the
 * sample is started when the returned publisher is subscribed and stopped on the terminal
 * signal (success, error or cancel).
 */
@Component
public class BenchmarkMetrics {

    private final MeterRegistry registry;

    public BenchmarkMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> Mono<T> time(String backend, String operation, Mono<T> action) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(registry);
            return action.doFinally(signal -> sample.stop(timer(backend, operation)));
        });
    }

    public <T> Flux<T> time(String backend, String operation, Flux<T> action) {
        return Flux.defer(() -> {
            Timer.Sample sample = Timer.start(registry);
            return action.doFinally(signal -> sample.stop(timer(backend, operation)));
        });
    }

    private Timer timer(String backend, String operation) {
        return Timer.builder("app.backend.operation")
                .tag("backend", backend)
                .tag("operation", operation)
                .register(registry);
    }
}
