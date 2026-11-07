package com.benchmark.nosqlblocking.cart;

import java.util.Map;

/**
 * Cart persistence abstraction. Two implementations exist, selected by
 * {@code app.cart.store}: Redis (hash with TTL) and MongoDB (document).
 */
public interface CartStore {

    Map<String, Integer> items(String cartId);

    Map<String, Integer> addItem(String cartId, String productId, int quantity);

    Map<String, Integer> updateItem(String cartId, String productId, int quantity);

    Map<String, Integer> removeItem(String cartId, String productId);

    void clear(String cartId);
}
