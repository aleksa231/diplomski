package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.OrderDtos.OrderItemDto;
import com.benchmark.common.dto.OrderDtos.OrderResponse;
import com.benchmark.common.dto.OrderDtos.OrderStatusHistoryDto;
import com.benchmark.common.dto.OrderDtos.PageOrderResponse;
import com.benchmark.nosqlblocking.cache.CacheConfig;
import com.benchmark.nosqlblocking.domain.OrderDocument;
import com.benchmark.nosqlblocking.domain.ProductDocument;
import com.benchmark.nosqlblocking.mapper.ProductMapper;
import com.benchmark.nosqlblocking.metrics.BenchmarkMetrics;
import com.benchmark.nosqlblocking.repository.OrderRepository;
import com.benchmark.nosqlblocking.repository.ProductRepository;
import com.mongodb.MongoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Order flow.
 *
 * <p>Stock is decremented with a conditional update ({@code stock >= qty}) so it can never
 * go negative; when the update matches zero documents the request is rejected with a
 * conflict (HTTP 409). Two modes are supported through {@code app.orders.transactions}:
 *
 * <ul>
 *   <li>{@code true} - stock decrement and order insert run in one multi-document
 *       transaction (requires a replica set), so a failure rolls everything back;</li>
 *   <li>{@code false} - the writes are sequential and a failure is compensated by
 *       incrementing back the stock that was already decremented.</li>
 * </ul>
 */
@Service
public class OrderService {

    /** Bounded retries for transient MongoDB transaction conflicts (e.g. WriteConflict, code 112). */
    private static final int MAX_TRANSIENT_ATTEMPTS = 8;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;
    private final CartService cartService;
    private final PopularityService popularityService;
    private final ProductMapper mapper;
    private final MongoTransactionManager transactionManager;
    private final boolean transactionsEnabled;
    private final BenchmarkMetrics metrics;
    private final CacheManager cacheManager;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        MongoTemplate mongoTemplate,
                        CartService cartService,
                        PopularityService popularityService,
                        ProductMapper mapper,
                        MongoTransactionManager transactionManager,
                        @Value("${app.orders.transactions:true}") boolean transactionsEnabled,
                        BenchmarkMetrics metrics,
                        CacheManager cacheManager) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
        this.cartService = cartService;
        this.popularityService = popularityService;
        this.mapper = mapper;
        this.transactionManager = transactionManager;
        this.transactionsEnabled = transactionsEnabled;
        this.metrics = metrics;
        this.cacheManager = cacheManager;
    }

    public OrderResponse createOrder(String cartId, String customerRef) {
        return createOrder(cartId, customerRef, null, null);
    }

    public OrderResponse createOrder(String cartId, String customerRef, String customerName, String customerEmail) {
        Map<String, Integer> cartItems = cartService.rawItems(cartId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty: " + cartId);
        }

        OrderResponse response = metrics.time("mongo", "orders.create", () -> transactionsEnabled
                ? withTransientRetry(() -> new TransactionTemplate(transactionManager)
                        .execute(status -> doCreate(customerRef, customerName, customerEmail, cartItems)))
                : withTransientRetry(() -> doCreateWithCompensation(customerRef, customerName, customerEmail, cartItems)));

        cartService.clear(cartId);
        evictCatalogCache();
        response.getItems().forEach(item -> popularityService.recordPurchase(item.getProductId(), item.getQuantity()));
        return response;
    }

    public OrderResponse getOrder(String orderId) {
        OrderDocument order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        return mapper.toResponse(order);
    }

    public PageOrderResponse findOrders(int page, int size, String query) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        Criteria criteria = null;
        if (query != null && !query.isBlank()) {
            String pattern = Pattern.quote(query.trim());
            criteria = new Criteria().orOperator(
                    Criteria.where("customerRef").regex(pattern, "i"),
                    Criteria.where("customerName").regex(pattern, "i"),
                    Criteria.where("customerEmail").regex(pattern, "i"));
        }

        Query mongoQuery = new Query();
        if (criteria != null) {
            mongoQuery.addCriteria(criteria);
        }
        mongoQuery.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        mongoQuery.skip((long) safePage * safeSize);
        mongoQuery.limit(safeSize);

        Query countQuery = new Query();
        if (criteria != null) {
            countQuery.addCriteria(criteria);
        }

        List<OrderResponse> content = mongoTemplate.find(mongoQuery, OrderDocument.class, "orders").stream()
                .map(mapper::toResponse).toList();
        long total = mongoTemplate.count(countQuery, OrderDocument.class, "orders");
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        return PageOrderResponse.builder()
                .content(content)
                .page(safePage)
                .size(safeSize)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    public OrderResponse updateStatus(String orderId, String status) {
        OrderDocument order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        order.setStatus(status);
        order.getStatusHistory().add(OrderStatusHistoryDto.builder().status(status).timestamp(Instant.now()).build());
        return mapper.toResponse(orderRepository.save(order));
    }

    private OrderResponse doCreate(String customerRef, String customerName, String customerEmail, Map<String, Integer> cartItems) {
        return doCreate(customerRef, customerName, customerEmail, cartItems, new ArrayList<>());
    }

    private OrderResponse doCreateWithCompensation(String customerRef, String customerName, String customerEmail, Map<String, Integer> cartItems) {
        List<OrderItemDto> decremented = new ArrayList<>();
        try {
            return doCreate(customerRef, customerName, customerEmail, cartItems, decremented);
        } catch (RuntimeException ex) {
            for (OrderItemDto item : decremented) {
                restoreStock(item.getProductId(), item.getQuantity());
            }
            throw ex;
        }
    }

    private OrderResponse doCreate(String customerRef, String customerName, String customerEmail, Map<String, Integer> cartItems, List<OrderItemDto> decremented) {
        List<OrderItemDto> items = new ArrayList<>();
        double total = 0.0;

        for (Map.Entry<String, Integer> entry : cartItems.entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();

            ProductDocument product = productRepository.findById(productId)
                    .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

            if (!decrementStock(productId, quantity)) {
                throw new IllegalStateException("Insufficient stock for product " + productId);
            }

            OrderItemDto item = OrderItemDto.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .price(product.getPrice())
                    .quantity(quantity)
                    .build();
            items.add(item);
            decremented.add(item);
            total += product.getPrice() * quantity;
        }

        OrderDocument order = OrderDocument.builder()
                .id(UUID.randomUUID().toString())
                .customerRef(customerRef)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .items(items)
                .total(total)
                .status("CREATED")
                .statusHistory(new ArrayList<>(List.of(
                        OrderStatusHistoryDto.builder().status("CREATED").timestamp(Instant.now()).build())))
                .createdAt(Instant.now())
                .build();

        return mapper.toResponse(orderRepository.save(order));
    }

    private void evictCatalogCache() {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    private boolean decrementStock(String productId, int quantity) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(productId).and("stock").gte(quantity)),
                new Update().inc("stock", -quantity),
                "products"
        ).getModifiedCount() == 1;
    }

    private void restoreStock(String productId, int quantity) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(productId)),
                new Update().inc("stock", quantity),
                "products");
    }

    /**
     * MongoDB aborts a transaction when it hits a write conflict (concurrent writes to the same
     * document). Such an abort is transient by contract - the server even labels it with
     * {@code TransientTransactionError} - so the whole operation is safe to retry. Without this,
     * a conflict under contention surfaces as an HTTP 500 instead of a retried success/409.
     */
    private <T> T withTransientRetry(Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException ex) {
                if (attempt >= MAX_TRANSIENT_ATTEMPTS || !isTransientConflict(ex)) {
                    throw ex;
                }
                backoff(attempt);
            }
        }
    }

    private static boolean isTransientConflict(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof MongoException mongo) {
                if (mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                        || mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    return true;
                }
                // 112 = WriteConflict, 251 = NoSuchTransaction (transaction already aborted).
                int code = mongo.getCode();
                if (code == 112 || code == 251) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void backoff(int attempt) {
        // Exponential cap with full jitter: de-correlates concurrent retriers so they do not
        // collide again in lockstep, which is what makes naive retry loops livelock.
        long cap = Math.min(400L, 10L << (attempt - 1));
        try {
            Thread.sleep(1L + ThreadLocalRandom.current().nextLong(cap));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying order transaction", ie);
        }
    }
}
