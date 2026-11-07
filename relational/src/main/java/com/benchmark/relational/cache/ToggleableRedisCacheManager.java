package com.benchmark.relational.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps the Spring Data Redis cache manager so that every returned cache is toggled,
 * failure-tolerant and instrumented. A single {@link CacheMetrics} instance is kept per
 * cache name to avoid re-registering the hit-ratio gauge.
 */
public class ToggleableRedisCacheManager implements CacheManager {

    private final RedisCacheManager delegate;
    private final CacheToggle toggle;
    private final MeterRegistry registry;
    private final Map<String, CacheMetrics> metricsByCache = new ConcurrentHashMap<>();

    public ToggleableRedisCacheManager(RedisCacheManager delegate, CacheToggle toggle, MeterRegistry registry) {
        this.delegate = delegate;
        this.toggle = toggle;
        this.registry = registry;
    }

    @Override
    @Nullable
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            return null;
        }
        return new MeteredCache(cache, toggle, metricsByCache.computeIfAbsent(name, n -> new CacheMetrics(registry, n)));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
