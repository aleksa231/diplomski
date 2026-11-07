package com.benchmark.nosqlreactive.cart;

import com.benchmark.nosqlreactive.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cart store: one hash per cart at {@code cart:{cartId}} (field = productId,
 * value = quantity). The 30 minute TTL is refreshed on every write. When Redis is
 * unreachable the operation fails fast with {@link ServiceUnavailableException} (HTTP 503)
 * instead of retrying the connection.
 */
@Component
@ConditionalOnProperty(name = "app.cart.store", havingValue = "redis", matchIfMissing = true)
public class RedisCartStore implements CartStore {

    private final ReactiveStringRedisTemplate redis;
    private final Duration ttl;

    public RedisCartStore(ReactiveStringRedisTemplate redis,
                          @Value("${app.cart.ttl-seconds:1800}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    private String key(String cartId) {
        return "cart:" + cartId;
    }

    @Override
    public Mono<Map<String, Integer>> items(String cartId) {
        return redis.<String, String>opsForHash().entries(key(cartId))
                .collectMap(Map.Entry::getKey, entry -> Integer.parseInt(entry.getValue()))
                .<Map<String, Integer>>map(HashMap::new)
                .onErrorMap(DataAccessException.class, ex -> unavailable("read", ex));
    }

    @Override
    public Mono<Map<String, Integer>> addItem(String cartId, String productId, int quantity) {
        String key = key(cartId);
        return redis.opsForHash().increment(key, productId, quantity)
                .then(redis.expire(key, ttl))
                .then(items(cartId))
                .onErrorMap(DataAccessException.class, ex -> unavailable("add", ex));
    }

    @Override
    public Mono<Map<String, Integer>> updateItem(String cartId, String productId, int quantity) {
        String key = key(cartId);
        return redis.opsForHash().put(key, productId, String.valueOf(quantity))
                .then(redis.expire(key, ttl))
                .then(items(cartId))
                .onErrorMap(DataAccessException.class, ex -> unavailable("update", ex));
    }

    @Override
    public Mono<Map<String, Integer>> removeItem(String cartId, String productId) {
        String key = key(cartId);
        return redis.opsForHash().remove(key, productId)
                .flatMap(removed -> redis.hasKey(key))
                .flatMap(exists -> Boolean.TRUE.equals(exists) ? redis.expire(key, ttl) : Mono.just(false))
                .then(items(cartId))
                .onErrorMap(DataAccessException.class, ex -> unavailable("remove", ex));
    }

    @Override
    public Mono<Void> clear(String cartId) {
        return redis.delete(key(cartId))
                .then()
                .onErrorMap(DataAccessException.class, ex -> unavailable("clear", ex));
    }

    private ServiceUnavailableException unavailable(String operation, DataAccessException cause) {
        return new ServiceUnavailableException("Redis cart store is unavailable during " + operation, cause);
    }
}
