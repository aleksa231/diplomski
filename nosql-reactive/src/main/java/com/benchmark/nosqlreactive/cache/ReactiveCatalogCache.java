package com.benchmark.nosqlreactive.cache;

import com.benchmark.common.dto.PageProductResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive Redis cache for catalog list queries.
 *
 * <p>Semantics mirror the blocking {@code MeteredCache}/{@code RedisCacheManager} pair:
 *
 * <ol>
 *   <li>keys are {@code cache:products:{normalizedQueryHash}} with a configurable TTL;</li>
 *   <li>when {@code app.cache.enabled} is false every read is a miss and every write is
 *       dropped, so the caller transparently falls back to the database;</li>
 *   <li>if Redis is unreachable the error is swallowed, the fallback counter is
 *       incremented and the request continues against MongoDB;</li>
 *   <li>hit/miss/put/eviction counters feed the hit-ratio gauge.</li>
 * </ol>
 */
@Component
public class ReactiveCatalogCache {

    private static final Logger log = LoggerFactory.getLogger(ReactiveCatalogCache.class);
    private static final String PREFIX = "cache:" + CacheConfig.PRODUCTS_CACHE + ":";

    private final ReactiveRedisTemplate<String, Object> redis;
    private final CacheToggle toggle;
    private final CacheMetrics metrics;
    private final Duration ttl;

    public ReactiveCatalogCache(@Qualifier(CacheConfig.CATALOG_TEMPLATE_BEAN) ReactiveRedisTemplate<String, Object> redis,
                                CacheToggle toggle,
                                MeterRegistry meterRegistry,
                                @Value("${app.cache.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.toggle = toggle;
        this.metrics = new CacheMetrics(meterRegistry, CacheConfig.PRODUCTS_CACHE);
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** Returns the cached page or an empty {@link Mono} on a miss / when the cache is disabled. */
    public Mono<PageProductResponse> get(String key) {
        if (!toggle.check()) {
            return Mono.empty();
        }
        return redis.opsForValue().get(redisKey(key))
                .map(PageProductResponse.class::cast)
                .doOnNext(value -> metrics.recordHit())
                .switchIfEmpty(Mono.defer(() -> {
                    metrics.recordMiss();
                    return Mono.empty();
                }))
                .onErrorResume(ex -> {
                    recordFallback("get", key, ex);
                    return Mono.empty();
                });
    }

    public Mono<Void> put(String key, PageProductResponse value) {
        if (!toggle.check()) {
            return Mono.empty();
        }
        return redis.opsForValue().set(redisKey(key), value, ttl)
                .doOnNext(ignored -> metrics.recordPut())
                .onErrorResume(ex -> {
                    recordFallback("put", key, ex);
                    return Mono.empty();
                })
                .then();
    }

    /** Drops every catalog entry; used for cache invalidation on writes and by the admin flush endpoint. */
    public Mono<Void> clear() {
        return redis.keys(PREFIX + "*")
                .flatMap(redis::delete)
                .then()
                .doOnSuccess(ignored -> metrics.recordEviction())
                .onErrorResume(ex -> {
                    recordFallback("clear", "-", ex);
                    return Mono.empty();
                });
    }

    private String redisKey(String key) {
        return PREFIX + key;
    }

    private void recordFallback(String operation, Object key, Throwable ex) {
        metrics.recordFallback();
        log.warn("Redis cache unavailable during {} (key={}), falling back to the database: {}",
                operation, key, ex.getMessage());
    }
}
