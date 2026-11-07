package com.benchmark.relational.service;

import com.benchmark.common.dto.OrderDtos.OrderResponse;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.relational.domain.OrderEntity;
import com.benchmark.relational.domain.OrderItem;
import com.benchmark.relational.domain.OrderStatusHistory;
import com.benchmark.relational.mapper.ProductMapper;
import com.benchmark.relational.metrics.BenchmarkMetrics;
import com.benchmark.relational.repository.OrderEntityRepository;
import com.benchmark.relational.store.ProductStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Order flow.
 *
 * <p>Stock is decremented with the single conditional update exposed by the active
 * {@link ProductStore} ({@code stock >= qty}); when it matches zero rows the whole transaction
 * rolls back and the request is rejected with a conflict (HTTP 409). Because everything runs in
 * one PostgreSQL transaction (stock decrement, order insert, cart clear), a failure leaves no
 * partial state behind - no compensating writes are needed.
 */
@Service
public class OrderService {

    private final OrderEntityRepository orderRepository;
    private final CartService cartService;
    private final PopularityService popularityService;
    private final ProductStore productStore;
    private final ProductMapper mapper;
    private final BenchmarkMetrics metrics;

    public OrderService(OrderEntityRepository orderRepository,
                        CartService cartService,
                        PopularityService popularityService,
                        ProductStore productStore,
                        ProductMapper mapper,
                        BenchmarkMetrics metrics) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.popularityService = popularityService;
        this.productStore = productStore;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Transactional
    public OrderResponse createOrder(String cartId, String customerRef) {
        Map<String, Integer> cartItems = cartService.rawItems(cartId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty: " + cartId);
        }

        OrderResponse response = metrics.time("postgres", "orders.create", () -> doCreate(customerRef, cartItems));

        cartService.clear(cartId);
        response.getItems().forEach(item -> popularityService.recordPurchase(item.getProductId(), item.getQuantity()));
        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        return mapper.toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(String orderId, String status) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        order.setStatus(status);
        order.getStatusHistory().add(
                new OrderStatusHistory(UUID.randomUUID().toString(), order, status, Instant.now()));
        return mapper.toResponse(orderRepository.save(order));
    }

    private OrderResponse doCreate(String customerRef, Map<String, Integer> cartItems) {
        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID().toString());
        order.setCustomerRef(customerRef);
        order.setStatus("CREATED");
        order.setCreatedAt(Instant.now());

        List<OrderItem> items = new ArrayList<>(cartItems.size());
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : cartItems.entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();

            ProductDto product = productStore.findById(productId)
                    .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

            if (!productStore.decrementStock(productId, quantity)) {
                throw new IllegalStateException("Insufficient stock for product " + productId);
            }

            BigDecimal price = BigDecimal.valueOf(product.getPrice());
            items.add(new OrderItem(UUID.randomUUID().toString(), order, productId,
                    product.getName(), price, quantity));
            total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
        }

        order.setItems(items);
        order.setTotal(total);
        order.setStatusHistory(new ArrayList<>(List.of(
                new OrderStatusHistory(UUID.randomUUID().toString(), order, "CREATED", Instant.now()))));

        return mapper.toResponse(orderRepository.save(order));
    }
}
