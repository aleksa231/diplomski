package com.benchmark.relational.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;

/**
 * Cache decorator that adds three behaviours on top of the raw Redis cache:
 *
 * <ol>
 *   <li>runtime toggle: when {@code app.cache.enabled} is false every read is a miss and
 *       every write is dropped, so the caller transparently falls back to the database;</li>
 *   <li>graceful degradation: if Redis is unreachable the exception is swallowed, the
 *       fallback counter is incremented and the request continues against PostgreSQL;</li>
 *   <li>instrumentation: hit/miss/put/eviction counters feeding the hit-ratio gauge.</li>
 * </ol>
 */
public class MeteredCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(MeteredCache.class);

    private final Cache delegate;
    private final CacheToggle toggle;
    private final CacheMetrics metrics;

    public MeteredCache(Cache delegate, CacheToggle toggle, CacheMetrics metrics) {
        this.delegate = delegate;
        this.toggle = toggle;
        this.metrics = metrics;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        if (!toggle.check()) {
            return null;
        }
        try {
            ValueWrapper wrapper = delegate.get(key);
            if (wrapper != null) {
                metrics.recordHit();
            } else {
                metrics.recordMiss();
            }
            return wrapper;
        } catch (RuntimeException ex) {
            recordFallback("get", key, ex);
            return null;
        }
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, @Nullable Class<T> type) {
        ValueWrapper wrapper = get(key);
        return wrapper == null ? null : (T) wrapper.get();
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception ex) {
            throw new ValueRetrievalException(key, valueLoader, ex);
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        if (!toggle.check()) {
            return;
        }
        try {
            delegate.put(key, value);
            metrics.recordPut();
        } catch (RuntimeException ex) {
            recordFallback("put", key, ex);
        }
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        if (!toggle.check()) {
            return null;
        }
        try {
            ValueWrapper wrapper = delegate.putIfAbsent(key, value);
            metrics.recordPut();
            return wrapper;
        } catch (RuntimeException ex) {
            recordFallback("putIfAbsent", key, ex);
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            delegate.evict(key);
            metrics.recordEviction();
        } catch (RuntimeException ex) {
            recordFallback("evict", key, ex);
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        try {
            boolean evicted = delegate.evictIfPresent(key);
            if (evicted) {
                metrics.recordEviction();
            }
            return evicted;
        } catch (RuntimeException ex) {
            recordFallback("evictIfPresent", key, ex);
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            delegate.clear();
            metrics.recordEviction();
        } catch (RuntimeException ex) {
            recordFallback("clear", "-", ex);
        }
    }

    @Override
    public boolean invalidate() {
        try {
            return delegate.invalidate();
        } catch (RuntimeException ex) {
            recordFallback("invalidate", "-", ex);
            return false;
        }
    }

    private void recordFallback(String operation, Object key, RuntimeException ex) {
        metrics.recordFallback();
        log.warn("Redis cache unavailable during {} (key={}), falling back to the database: {}",
                operation, key, ex.getMessage());
    }
}
