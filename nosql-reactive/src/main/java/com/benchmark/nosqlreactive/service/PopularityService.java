package com.benchmark.nosqlreactive.service;

import com.benchmark.common.dto.ReportDtos.PopularItemResponse;
import com.benchmark.nosqlreactive.metrics.BenchmarkMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Popularity ranking backed by Redis sorted sets (ZINCRBY on product read / purchase).
 * Writes and reads are best effort: if Redis is down the operation is skipped and the
 * callers keep working, which keeps the cart/catalog paths independent of Redis health.
 */
@Service
public class PopularityService {

    private static final Logger log = LoggerFactory.getLogger(PopularityService.class);
    private static final String VIEWS_KEY = "popularity:views";
    private static final String PURCHASES_KEY = "popularity:purchases";

    private final ReactiveStringRedisTemplate redis;
    private final BenchmarkMetrics metrics;

    public PopularityService(ReactiveStringRedisTemplate redis, BenchmarkMetrics metrics) {
        this.redis = redis;
        this.metrics = metrics;
    }

    public Mono<Void> recordView(String productId) {
        return increment(VIEWS_KEY, productId, 1.0, "popularity.view");
    }

    public Mono<Void> recordPurchase(String productId, int quantity) {
        return increment(PURCHASES_KEY, productId, quantity, "popularity.purchase");
    }

    public Mono<List<PopularItemResponse>> top(String type, int limit) {
        String key = keyFor(type);
        return metrics.time("redis", "popularity.top",
                redis.opsForZSet().reverseRangeWithScores(key, Range.closed(0L, (long) Math.max(limit, 1) - 1))
                        .map(tuple -> PopularItemResponse.builder()
                                .productId(tuple.getValue())
                                .score(tuple.getScore())
                                .build())
                        .collectList()
                        .onErrorResume(ex -> {
                            log.warn("Popularity ranking unavailable (Redis): {}", ex.getMessage());
                            return Mono.just(List.of());
                        }));
    }

    private Mono<Void> increment(String key, String productId, double delta, String operation) {
        return metrics.time("redis", operation,
                redis.opsForZSet().incrementScore(key, productId, delta)
                        .then()
                        .onErrorResume(ex -> {
                            log.warn("Skipping {} for {} - Redis unavailable: {}", operation, productId, ex.getMessage());
                            return Mono.empty();
                        }));
    }

    private String keyFor(String type) {
        return "purchases".equalsIgnoreCase(type) ? PURCHASES_KEY : VIEWS_KEY;
    }
}
