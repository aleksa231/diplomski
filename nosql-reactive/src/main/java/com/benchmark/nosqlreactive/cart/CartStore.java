package com.benchmark.nosqlreactive.cart;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Cart persistence abstraction. Two implementations exist, selected by
 * {@code app.cart.store}: Redis (hash with TTL) and MongoDB (document). All operations
 * return a {@link Mono} so the whole cart path stays non-blocking.
 */
public interface CartStore {

    Mono<Map<String, Integer>> items(String cartId);

    Mono<Map<String, Integer>> addItem(String cartId, String productId, int quantity);

    Mono<Map<String, Integer>> updateItem(String cartId, String productId, int quantity);

    Mono<Map<String, Integer>> removeItem(String cartId, String productId);

    Mono<Void> clear(String cartId);
}
