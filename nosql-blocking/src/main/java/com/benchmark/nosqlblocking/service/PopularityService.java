package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.ReportDtos.PopularItemResponse;
import com.benchmark.nosqlblocking.metrics.BenchmarkMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private final StringRedisTemplate redis;
    private final BenchmarkMetrics metrics;

    public PopularityService(StringRedisTemplate redis, BenchmarkMetrics metrics, MeterRegistry registry) {
        this.redis = redis;
        this.metrics = metrics;
    }

    public void recordView(String productId) {
        increment(VIEWS_KEY, productId, 1.0, "popularity.view");
    }

    public void recordPurchase(String productId, int quantity) {
        increment(PURCHASES_KEY, productId, quantity, "popularity.purchase");
    }

    public List<PopularItemResponse> top(String type, int limit) {
        String key = keyFor(type);
        return metrics.time("redis", "popularity.top", () -> {
            try {
                Set<TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, Math.max(limit, 1) - 1);
                List<PopularItemResponse> result = new ArrayList<>();
                if (tuples != null) {
                    for (TypedTuple<String> tuple : tuples) {
                        result.add(PopularItemResponse.builder()
                                .productId(tuple.getValue())
                                .score(tuple.getScore())
                                .build());
                    }
                }
                return result;
            } catch (RuntimeException ex) {
                log.warn("Popularity ranking unavailable (Redis): {}", ex.getMessage());
                return List.of();
            }
        });
    }

    private void increment(String key, String productId, double delta, String operation) {
        metrics.timeVoid("redis", operation, () -> {
            try {
                redis.opsForZSet().incrementScore(key, productId, delta);
            } catch (RuntimeException ex) {
                log.warn("Skipping {} for {} - Redis unavailable: {}", operation, productId, ex.getMessage());
            }
        });
    }

    private String keyFor(String type) {
        return "purchases".equalsIgnoreCase(type) ? PURCHASES_KEY : VIEWS_KEY;
    }
}
