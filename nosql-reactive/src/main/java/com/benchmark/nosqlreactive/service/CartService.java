package com.benchmark.nosqlreactive.service;

import com.benchmark.common.dto.CartDtos.CartItemDto;
import com.benchmark.common.dto.CartDtos.CartResponse;
import com.benchmark.nosqlreactive.cart.CartStore;
import com.benchmark.nosqlreactive.metrics.BenchmarkMetrics;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Cart operations on top of the configured {@link CartStore}. Cart reads are cheap by
 * design (Redis hash / single Mongo document) which is what S2 measures.
 */
@Service
public class CartService {

    private final CartStore cartStore;
    private final BenchmarkMetrics metrics;

    public CartService(CartStore cartStore, BenchmarkMetrics metrics) {
        this.cartStore = cartStore;
        this.metrics = metrics;
    }

    public Mono<CartResponse> getCart(String cartId) {
        return metrics.time("cart-store", "cart.items", cartStore.items(cartId))
                .map(items -> toResponse(cartId, items));
    }

    public Mono<CartResponse> addItem(String cartId, String productId, int quantity) {
        return metrics.time("cart-store", "cart.add", cartStore.addItem(cartId, productId, quantity))
                .map(items -> toResponse(cartId, items));
    }

    public Mono<CartResponse> updateItem(String cartId, String productId, int quantity) {
        return metrics.time("cart-store", "cart.update", cartStore.updateItem(cartId, productId, quantity))
                .map(items -> toResponse(cartId, items));
    }

    public Mono<CartResponse> removeItem(String cartId, String productId) {
        return metrics.time("cart-store", "cart.remove", cartStore.removeItem(cartId, productId))
                .map(items -> toResponse(cartId, items));
    }

    public Mono<Void> clear(String cartId) {
        return metrics.time("cart-store", "cart.clear", cartStore.clear(cartId));
    }

    /** Raw view of the cart contents used by the order flow. */
    public Mono<Map<String, Integer>> rawItems(String cartId) {
        return metrics.time("cart-store", "cart.items", cartStore.items(cartId));
    }

    private CartResponse toResponse(String cartId, Map<String, Integer> items) {
        List<CartItemDto> dtoItems = items.entrySet().stream()
                .map(e -> CartItemDto.builder().productId(e.getKey()).quantity(e.getValue()).build())
                .toList();
        return CartResponse.builder().cartId(cartId).items(dtoItems).build();
    }
}
