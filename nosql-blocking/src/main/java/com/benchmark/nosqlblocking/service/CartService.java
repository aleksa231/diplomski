package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.CartDtos.CartItemDto;
import com.benchmark.common.dto.CartDtos.CartResponse;
import com.benchmark.nosqlblocking.cart.CartStore;
import com.benchmark.nosqlblocking.metrics.BenchmarkMetrics;
import org.springframework.stereotype.Service;

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

    public CartResponse getCart(String cartId) {
        return toResponse(cartId, metrics.time("cart-store", "cart.items", () -> cartStore.items(cartId)));
    }

    public CartResponse addItem(String cartId, String productId, int quantity) {
        return toResponse(cartId, metrics.time("cart-store", "cart.add",
                () -> cartStore.addItem(cartId, productId, quantity)));
    }

    public CartResponse updateItem(String cartId, String productId, int quantity) {
        return toResponse(cartId, metrics.time("cart-store", "cart.update",
                () -> cartStore.updateItem(cartId, productId, quantity)));
    }

    public CartResponse removeItem(String cartId, String productId) {
        return toResponse(cartId, metrics.time("cart-store", "cart.remove",
                () -> cartStore.removeItem(cartId, productId)));
    }

    public void clear(String cartId) {
        metrics.timeVoid("cart-store", "cart.clear", () -> cartStore.clear(cartId));
    }

    /** Raw view of the cart contents used by the order flow. */
    public Map<String, Integer> rawItems(String cartId) {
        return metrics.time("cart-store", "cart.items", () -> cartStore.items(cartId));
    }

    private CartResponse toResponse(String cartId, Map<String, Integer> items) {
        List<CartItemDto> dtoItems = items.entrySet().stream()
                .map(e -> CartItemDto.builder().productId(e.getKey()).quantity(e.getValue()).build())
                .toList();
        return CartResponse.builder().cartId(cartId).items(dtoItems).build();
    }
}
