package com.benchmark.nosqlblocking.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Per-cache Micrometer instrumentation: hit/miss counters, a derived hit-ratio gauge,
 * plus counters for puts, evictions and Redis fallbacks (cache unavailable).
 */
public class CacheMetrics {

    private final Counter hits;
    private final Counter misses;
    private final Counter puts;
    private final Counter evictions;
    private final Counter fallbacks;
    @SuppressWarnings("unused")
    private final Gauge hitRatioGauge;

    public CacheMetrics(MeterRegistry registry, String cacheName) {
        this.hits = registry.counter("cache.gets", "cache", cacheName, "result", "hit");
        this.misses = registry.counter("cache.gets", "cache", cacheName, "result", "miss");
        this.puts = registry.counter("cache.puts", "cache", cacheName);
        this.evictions = registry.counter("cache.evictions", "cache", cacheName);
        this.fallbacks = registry.counter("cache.redis.fallback", "cache", cacheName);
        // Hold a strong reference so the gauge is not garbage collected by Micrometer.
        this.hitRatioGauge = Gauge.builder("cache.hit.ratio", this, CacheMetrics::hitRatio)
                .tag("cache", cacheName)
                .register(registry);
    }

    public double hitRatio() {
        double hit = hits.count();
        double miss = misses.count();
        double total = hit + miss;
        return total == 0.0 ? 0.0 : hit / total;
    }

    public void recordHit() {
        hits.increment();
    }

    public void recordMiss() {
        misses.increment();
    }

    public void recordPut() {
        puts.increment();
    }

    public void recordEviction() {
        evictions.increment();
    }

    public void recordFallback() {
        fallbacks.increment();
    }
}
