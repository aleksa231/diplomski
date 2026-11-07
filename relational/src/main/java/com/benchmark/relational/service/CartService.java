package com.benchmark.relational.service;

import com.benchmark.common.dto.CartDtos.CartItemDto;
import com.benchmark.common.dto.CartDtos.CartResponse;
import com.benchmark.relational.domain.Cart;
import com.benchmark.relational.domain.CartItem;
import com.benchmark.relational.metrics.BenchmarkMetrics;
import com.benchmark.relational.repository.CartItemRepository;
import com.benchmark.relational.repository.CartRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cart operations backed by the relational {@code cart}/{@code cart_item} tables. Entries carry
 * an {@code expires_at} timestamp and a scheduled task purges expired carts, replacing the TTL
 * that the Redis cart store gets for free in the NoSQL stack.
 *
 * <p>The {@code cart.id} column is a fixed 36 character key, so the caller-supplied cart token
 * (which may be longer) is mapped to a deterministic 32 character key. The original token is
 * always echoed back in the response, so the mapping stays internal.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final long ttlSeconds;
    private final BenchmarkMetrics metrics;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       @Value("${app.cart.ttl-seconds:1800}") long ttlSeconds,
                       BenchmarkMetrics metrics) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.ttlSeconds = ttlSeconds;
        this.metrics = metrics;
    }

    @Transactional
    public CartResponse getCart(String cartId) {
        return toResponse(cartId, metrics.time("postgres", "cart.items", () -> items(cartId)));
    }

    @Transactional
    public CartResponse addItem(String cartId, String productId, int quantity) {
        Map<String, Integer> items = metrics.time("postgres", "cart.add", () -> {
            Cart cart = ensureCart(cartId);
            cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                    .ifPresentOrElse(
                            item -> {
                                item.setQuantity(item.getQuantity() + quantity);
                                cartItemRepository.save(item);
                            },
                            () -> cartItemRepository.save(
                                    new CartItem(UUID.randomUUID().toString(), cart, productId, quantity)));
            return items(cartId);
        });
        return toResponse(cartId, items);
    }

    @Transactional
    public CartResponse updateItem(String cartId, String productId, int quantity) {
        Map<String, Integer> items = metrics.time("postgres", "cart.update", () -> {
            Cart cart = ensureCart(cartId);
            cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                    .ifPresentOrElse(
                            item -> {
                                item.setQuantity(quantity);
                                cartItemRepository.save(item);
                            },
                            () -> cartItemRepository.save(
                                    new CartItem(UUID.randomUUID().toString(), cart, productId, quantity)));
            return items(cartId);
        });
        return toResponse(cartId, items);
    }

    @Transactional
    public CartResponse removeItem(String cartId, String productId) {
        Map<String, Integer> items = metrics.time("postgres", "cart.remove", () -> {
            String id = recordId(cartId);
            if (cartRepository.existsById(id)) {
                cartItemRepository.deleteByCartIdAndProductId(id, productId);
            }
            return items(cartId);
        });
        return toResponse(cartId, items);
    }

    @Transactional
    public void clear(String cartId) {
        metrics.timeVoid("postgres", "cart.clear", () ->
                cartRepository.findById(recordId(cartId)).ifPresent(cartRepository::delete));
    }

    /** Raw view of the cart contents used by the order flow. */
    @Transactional(readOnly = true)
    public Map<String, Integer> rawItems(String cartId) {
        return metrics.time("postgres", "cart.items", () -> items(cartId));
    }

    @Scheduled(fixedDelayString = "${app.cart.cleanup-interval-ms:60000}", initialDelayString = "${app.cart.cleanup-interval-ms:60000}")
    @Transactional
    public void purgeExpiredCarts() {
        cartRepository.deleteExpired(Instant.now());
    }

    private Map<String, Integer> items(String cartId) {
        String id = recordId(cartId);
        List<CartItem> cartItems = cartItemRepository.findByCartId(id);
        Map<String, Integer> items = new LinkedHashMap<>();
        for (CartItem item : cartItems) {
            items.put(item.getProductId(), item.getQuantity());
        }
        return items;
    }

    private Cart ensureCart(String cartId) {
        String id = recordId(cartId);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        return cartRepository.findById(id)
                .map(cart -> {
                    cart.setExpiresAt(expiresAt);
                    return cartRepository.save(cart);
                })
                .orElseGet(() -> cartRepository.saveAndFlush(new Cart(id, now, expiresAt)));
    }

    private CartResponse toResponse(String cartId, Map<String, Integer> items) {
        List<CartItemDto> dtoItems = items.entrySet().stream()
                .map(e -> CartItemDto.builder().productId(e.getKey()).quantity(e.getValue()).build())
                .toList();
        return CartResponse.builder().cartId(cartId).items(dtoItems).build();
    }

    private static String recordId(String cartId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cartId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
