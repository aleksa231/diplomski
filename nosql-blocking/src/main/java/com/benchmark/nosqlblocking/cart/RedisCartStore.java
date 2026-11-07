package com.benchmark.nosqlblocking.cart;

import com.benchmark.nosqlblocking.exception.ServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cart store: one hash per cart at {@code cart:{cartId}} (field = productId,
 * value = quantity). The 30 minute TTL is refreshed on every write. When Redis is
 * unreachable the operation fails fast with {@link ServiceUnavailableException} (HTTP 503)
 * instead of blocking on connection retries.
 */
@Component
@ConditionalOnProperty(name = "app.cart.store", havingValue = "redis", matchIfMissing = true)
public class RedisCartStore implements CartStore {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisCartStore(StringRedisTemplate redis,
                          @org.springframework.beans.factory.annotation.Value("${app.cart.ttl-seconds:1800}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    private String key(String cartId) {
        return "cart:" + cartId;
    }

    @Override
    public Map<String, Integer> items(String cartId) {
        try {
            Map<Object, Object> entries = redis.opsForHash().entries(key(cartId));
            Map<String, Integer> result = new HashMap<>();
            entries.forEach((k, v) -> result.put(String.valueOf(k), Integer.parseInt(String.valueOf(v))));
            return result;
        } catch (DataAccessException ex) {
            throw unavailable("read", ex);
        }
    }

    @Override
    public Map<String, Integer> addItem(String cartId, String productId, int quantity) {
        try {
            String key = key(cartId);
            redis.opsForHash().increment(key, productId, quantity);
            redis.expire(key, ttl);
            return items(cartId);
        } catch (DataAccessException ex) {
            throw unavailable("add", ex);
        }
    }

    @Override
    public Map<String, Integer> updateItem(String cartId, String productId, int quantity) {
        try {
            String key = key(cartId);
            redis.opsForHash().put(key, productId, String.valueOf(quantity));
            redis.expire(key, ttl);
            return items(cartId);
        } catch (DataAccessException ex) {
            throw unavailable("update", ex);
        }
    }

    @Override
    public Map<String, Integer> removeItem(String cartId, String productId) {
        try {
            String key = key(cartId);
            redis.opsForHash().delete(key, productId);
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                redis.expire(key, ttl);
            }
            return items(cartId);
        } catch (DataAccessException ex) {
            throw unavailable("remove", ex);
        }
    }

    @Override
    public void clear(String cartId) {
        try {
            redis.delete(key(cartId));
        } catch (DataAccessException ex) {
            throw unavailable("clear", ex);
        }
    }

    private ServiceUnavailableException unavailable(String operation, DataAccessException cause) {
        return new ServiceUnavailableException("Redis cart store is unavailable during " + operation, cause);
    }
}
