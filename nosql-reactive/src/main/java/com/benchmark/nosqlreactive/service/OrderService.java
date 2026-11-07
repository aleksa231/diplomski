package com.benchmark.nosqlreactive.service;

import com.benchmark.common.dto.OrderDtos.OrderItemDto;
import com.benchmark.common.dto.OrderDtos.OrderResponse;
import com.benchmark.common.dto.OrderDtos.OrderStatusHistoryDto;
import com.benchmark.nosqlreactive.domain.OrderDocument;
import com.benchmark.nosqlreactive.mapper.ProductMapper;
import com.benchmark.nosqlreactive.metrics.BenchmarkMetrics;
import com.benchmark.nosqlreactive.repository.OrderRepository;
import com.benchmark.nosqlreactive.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Order flow.
 *
 * <p>Stock is decremented with a conditional update ({@code stock >= qty}) so it can never
 * go negative; when the update matches zero documents the request is rejected with a
 * conflict (HTTP 409). Two modes are supported through {@code app.orders.transactions}:
 *
 * <ul>
 *   <li>{@code true} - stock decrement and order insert run in one multi-document
 *       transaction (requires a replica set) driven by a {@link TransactionalOperator}, so
 *       a failure rolls everything back;</li>
 *   <li>{@code false} - the writes are sequential and a failure is compensated by
 *       incrementing back the stock that was already decremented.</li>
 * </ul>
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final CartService cartService;
    private final PopularityService popularityService;
    private final ProductMapper mapper;
    private final TransactionalOperator transactionalOperator;
    private final boolean transactionsEnabled;
    private final BenchmarkMetrics metrics;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        ReactiveMongoTemplate mongoTemplate,
                        CartService cartService,
                        PopularityService popularityService,
                        ProductMapper mapper,
                        ReactiveMongoTransactionManager transactionManager,
                        @Value("${app.orders.transactions:true}") boolean transactionsEnabled,
                        BenchmarkMetrics metrics) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
        this.cartService = cartService;
        this.popularityService = popularityService;
        this.mapper = mapper;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.transactionsEnabled = transactionsEnabled;
        this.metrics = metrics;
    }

    public Mono<OrderResponse> createOrder(String cartId, String customerRef) {
        return cartService.rawItems(cartId)
                .flatMap(cartItems -> {
                    if (cartItems.isEmpty()) {
                        return Mono.<OrderResponse>error(new IllegalArgumentException("Cart is empty: " + cartId));
                    }
                    return metrics.time("mongo", "orders.create", transactionsEnabled
                            ? transactionalOperator.transactional(doCreate(customerRef, cartItems))
                            : doCreateWithCompensation(customerRef, cartItems));
                })
                .flatMap(response -> cartService.clear(cartId)
                        .then(Flux.fromIterable(response.getItems())
                                .concatMap(item -> popularityService.recordPurchase(item.getProductId(), item.getQuantity()))
                                .then())
                        .thenReturn(response));
    }

    public Mono<OrderResponse> getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Order not found: " + orderId)))
                .map(mapper::toResponse);
    }

    public Mono<OrderResponse> updateStatus(String orderId, String status) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Order not found: " + orderId)))
                .flatMap(order -> {
                    order.setStatus(status);
                    order.getStatusHistory().add(OrderStatusHistoryDto.builder()
                            .status(status)
                            .timestamp(Instant.now())
                            .build());
                    return orderRepository.save(order);
                })
                .map(mapper::toResponse);
    }

    private Mono<OrderResponse> doCreate(String customerRef, Map<String, Integer> cartItems) {
        return doCreate(customerRef, cartItems, new ArrayList<>());
    }

    private Mono<OrderResponse> doCreateWithCompensation(String customerRef, Map<String, Integer> cartItems) {
        return Mono.defer(() -> {
            List<OrderItemDto> decremented = new ArrayList<>();
            return doCreate(customerRef, cartItems, decremented)
                    .onErrorResume(ex -> Flux.fromIterable(new ArrayList<>(decremented))
                            .concatMap(item -> restoreStock(item.getProductId(), item.getQuantity()))
                            .then(Mono.error(ex)));
        });
    }

    private Mono<OrderResponse> doCreate(String customerRef,
                                         Map<String, Integer> cartItems,
                                         List<OrderItemDto> decremented) {
        return Flux.fromIterable(cartItems.entrySet())
                .concatMap(entry -> {
                    String productId = entry.getKey();
                    int quantity = entry.getValue();
                    return productRepository.findById(productId)
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Product not found: " + productId)))
                            .flatMap(product -> decrementStock(productId, quantity)
                                    .flatMap(decrementedOk -> {
                                        if (!Boolean.TRUE.equals(decrementedOk)) {
                                            return Mono.<OrderItemDto>error(
                                                    new IllegalStateException("Insufficient stock for product " + productId));
                                        }
                                        OrderItemDto item = OrderItemDto.builder()
                                                .productId(productId)
                                                .productName(product.getName())
                                                .price(product.getPrice())
                                                .quantity(quantity)
                                                .build();
                                        decremented.add(item);
                                        return Mono.just(item);
                                    }));
                })
                .collectList()
                .flatMap(items -> {
                    double total = items.stream()
                            .mapToDouble(item -> item.getPrice() * item.getQuantity())
                            .sum();

                    OrderDocument order = OrderDocument.builder()
                            .id(UUID.randomUUID().toString())
                            .customerRef(customerRef)
                            .items(items)
                            .total(total)
                            .status("CREATED")
                            .statusHistory(new ArrayList<>(List.of(OrderStatusHistoryDto.builder()
                                    .status("CREATED")
                                    .timestamp(Instant.now())
                                    .build())))
                            .createdAt(Instant.now())
                            .build();

                    return orderRepository.save(order);
                })
                .map(mapper::toResponse);
    }

    private Mono<Boolean> decrementStock(String productId, int quantity) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(productId).and("stock").gte(quantity)),
                        new Update().inc("stock", -quantity),
                        "products")
                .map(result -> result.getModifiedCount() == 1);
    }

    private Mono<Void> restoreStock(String productId, int quantity) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(productId)),
                        new Update().inc("stock", quantity),
                        "products")
                .then();
    }
}
