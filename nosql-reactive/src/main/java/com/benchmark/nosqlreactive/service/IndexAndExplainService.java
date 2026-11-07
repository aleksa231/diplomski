package com.benchmark.nosqlreactive.service;

import com.benchmark.common.dto.AdminDtos.ExplainResponse;
import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import com.mongodb.client.model.Indexes;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reactor-native counterpart of the blocking {@code IndexAndExplainService}: index state
 * detection, the S4 index toggle and the Q1..Q5 explain plans, all driven through the
 * reactive MongoDB driver ({@code listIndexes}, {@code createIndex}, {@code dropIndex},
 * {@code find().explain()}, {@code aggregate().explain()}).
 *
 * <p>The experiment indexes are managed only through the admin toggle (S4), never created
 * implicitly, so the starting state is derived from what actually exists in the collection.
 * Because a {@code @PostConstruct} hook cannot await a reactive call, the detection is
 * resolved lazily on the first read of {@link #indexesEnabled()} and cached afterwards (the
 * toggle also marks the state as resolved).
 */
@Service
public class IndexAndExplainService {

    private static final Logger log = LoggerFactory.getLogger(IndexAndExplainService.class);
    private static final String PRODUCTS = "products";
    private static final String ORDERS = "orders";

    private final ReactiveMongoTemplate mongoTemplate;
    private final AtomicBoolean indexesEnabled = new AtomicBoolean(false);
    private final AtomicBoolean indexStateResolved = new AtomicBoolean(false);

    public IndexAndExplainService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Mono<Boolean> indexesEnabled() {
        if (indexStateResolved.get()) {
            return Mono.just(indexesEnabled.get());
        }
        return textIndexExists().doOnNext(exists -> {
            indexesEnabled.set(exists);
            indexStateResolved.set(true);
        });
    }

    public Mono<IndexToggleResponse> toggleIndexes(boolean enabled) {
        return mongoTemplate.getCollection(PRODUCTS)
                .flatMap(products -> mongoTemplate.getCollection(ORDERS)
                        .flatMap(orders -> enabled
                                ? createIndexes(products, orders)
                                : dropIndexes(products, orders)));
    }

    public Mono<ExplainResponse> explain(String queryType) {
        return explainPlan(queryType)
                .map(plan -> ExplainResponse.builder()
                        .query(queryType)
                        .plan(plan.toJson())
                        .build());
    }

    private Mono<IndexToggleResponse> createIndexes(MongoCollection<Document> products,
                                                    MongoCollection<Document> orders) {
        log.info("[MongoDB] Creating experiment indexes for S4...");
        return Mono.from(products.createIndex(Indexes.ascending("category", "price")))
                .then(Mono.from(products.createIndex(Indexes.ascending("category", "attributes.ramGb"))))
                .then(Mono.from(products.createIndex(Indexes.text("name"))))
                .then(Mono.from(orders.createIndex(Indexes.ascending("createdAt"))))
                .then(Mono.from(orders.createIndex(Indexes.ascending("items.productId"))))
                .then(markIndexState(true))
                .thenReturn(IndexToggleResponse.builder()
                        .indexesEnabled(true)
                        .message("Experiment indexes created in MongoDB")
                        .build());
    }

    private Mono<IndexToggleResponse> dropIndexes(MongoCollection<Document> products,
                                                  MongoCollection<Document> orders) {
        log.info("[MongoDB] Dropping experiment indexes for S4...");
        return dropIndexQuietly(products, "category_1_price_1")
                .then(dropIndexQuietly(products, "category_1_attributes.ramGb_1"))
                .then(dropIndexQuietly(products, "name_text"))
                .then(dropIndexQuietly(orders, "createdAt_1"))
                .then(dropIndexQuietly(orders, "items.productId_1"))
                .then(markIndexState(false))
                .thenReturn(IndexToggleResponse.builder()
                        .indexesEnabled(false)
                        .message("Experiment indexes dropped from MongoDB")
                        .build());
    }

    private Mono<Void> dropIndexQuietly(MongoCollection<Document> collection, String indexName) {
        return Mono.from(collection.dropIndex(indexName))
                .onErrorResume(ex -> Mono.empty());
    }

    private Mono<Void> markIndexState(boolean enabled) {
        return Mono.fromRunnable(() -> {
            indexesEnabled.set(enabled);
            indexStateResolved.set(true);
        });
    }

    private Mono<Boolean> textIndexExists() {
        return mongoTemplate.getCollection(PRODUCTS)
                .flatMapMany(MongoCollection::listIndexes)
                .map(index -> index.get("key", Document.class))
                .filter(key -> key != null && "text".equals(key.getString("name")))
                .hasElements()
                .onErrorResume(ex -> {
                    log.warn("[MongoDB] Unable to inspect product indexes: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Explains the queries S4 actually runs (plus full-dataset contrasts), mirroring the blocking
     * service: Q1=catalog (category+price), Q2=sales-by-category window, Q3=top-products window,
     * Q4=sales-by-category full, Q5=top-products full. Window matches the S4 k6 script.
     */
    private Mono<Document> explainPlan(String queryType) {
        String type = queryType == null ? "" : queryType.toUpperCase();
        java.util.Date from = java.util.Date.from(java.time.Instant.parse("2024-01-05T00:00:00Z"));
        java.util.Date to = java.util.Date.from(java.time.Instant.parse("2024-01-05T06:00:00Z"));
        return switch (type) {
            case "Q1" -> mongoTemplate.getCollection(PRODUCTS).flatMap(products ->
                    Mono.from(products.find(new Document("category", "laptops"))
                            .sort(new Document("price", 1)).limit(20).explain()));
            case "Q2" -> mongoTemplate.getCollection(ORDERS).flatMap(orders ->
                    Mono.from(orders.aggregate(salesByCategoryPipeline(from, to)).explain()));
            case "Q3" -> mongoTemplate.getCollection(ORDERS).flatMap(orders ->
                    Mono.from(orders.aggregate(topProductsPipeline(from, to, 10)).explain()));
            case "Q4" -> mongoTemplate.getCollection(ORDERS).flatMap(orders ->
                    Mono.from(orders.aggregate(salesByCategoryPipeline(null, null)).explain()));
            case "Q5" -> mongoTemplate.getCollection(ORDERS).flatMap(orders ->
                    Mono.from(orders.aggregate(topProductsPipeline(null, null, 10)).explain()));
            case "Q6" -> mongoTemplate.getCollection(ORDERS).flatMap(orders ->
                    Mono.from(orders.aggregate(orderTotalsPipeline(from, to)).explain()));
            case "Q7" -> mongoTemplate.getCollection(ORDERS).flatMap(orders ->
                    Mono.from(orders.aggregate(orderTotalsPipeline(null, null)).explain()));
            default -> Mono.error(new IllegalArgumentException("Unknown query for explain: " + queryType));
        };
    }

    private List<Document> salesByCategoryPipeline(java.util.Date from, java.util.Date to) {
        java.util.List<Document> p = new java.util.ArrayList<>();
        if (from != null || to != null) {
            Document range = new Document();
            if (from != null) range.append("$gte", from);
            if (to != null) range.append("$lte", to);
            p.add(new Document("$match", new Document("createdAt", range)));
        }
        p.add(new Document("$unwind", "$items"));
        p.add(new Document("$lookup", new Document("from", "products")
                .append("localField", "items.productId").append("foreignField", "_id").append("as", "prod")));
        p.add(new Document("$unwind", "$prod"));
        p.add(new Document("$group", new Document("_id", "$prod.category")
                .append("orderIds", new Document("$addToSet", "$_id"))
                .append("totalRevenue", new Document("$sum",
                        new Document("$multiply", java.util.List.of("$items.price", "$items.quantity"))))));
        p.add(new Document("$project", new Document("category", "$_id").append("_id", 0)
                .append("orderCount", new Document("$size", "$orderIds"))
                .append("totalRevenue", 1)));
        p.add(new Document("$sort", new Document("totalRevenue", -1)));
        return p;
    }

    private List<Document> topProductsPipeline(java.util.Date from, java.util.Date to, int limit) {
        java.util.List<Document> p = new java.util.ArrayList<>();
        if (from != null || to != null) {
            Document range = new Document();
            if (from != null) range.append("$gte", from);
            if (to != null) range.append("$lte", to);
            p.add(new Document("$match", new Document("createdAt", range)));
        }
        p.add(new Document("$unwind", "$items"));
        p.add(new Document("$lookup", new Document("from", "products")
                .append("localField", "items.productId").append("foreignField", "_id").append("as", "prod")));
        p.add(new Document("$unwind", "$prod"));
        p.add(new Document("$group", new Document("_id", "$items.productId")
                .append("productName", new Document("$first", "$items.productName"))
                .append("category", new Document("$first", "$prod.category"))
                .append("unitsSold", new Document("$sum", "$items.quantity"))
                .append("revenue", new Document("$sum",
                        new Document("$multiply", java.util.List.of("$items.price", "$items.quantity"))))));
        p.add(new Document("$sort", new Document("unitsSold", -1)));
        p.add(new Document("$limit", limit));
        return p;
    }

    /** Mirrors {@code ReportService.getOrderTotals} (no $lookup) — isolates index impact for S4B. */
    private List<Document> orderTotalsPipeline(java.util.Date from, java.util.Date to) {
        java.util.List<Document> p = new java.util.ArrayList<>();
        if (from != null || to != null) {
            Document range = new Document();
            if (from != null) range.append("$gte", from);
            if (to != null) range.append("$lte", to);
            p.add(new Document("$match", new Document("createdAt", range)));
        }
        p.add(new Document("$unwind", "$items"));
        p.add(new Document("$group", new Document("_id", "$_id")
                .append("orderRevenue", new Document("$sum",
                        new Document("$multiply", java.util.List.of("$items.price", "$items.quantity"))))
                .append("orderUnits", new Document("$sum", "$items.quantity"))));
        p.add(new Document("$group", new Document("_id", null)
                .append("orderCount", new Document("$sum", 1))
                .append("totalRevenue", new Document("$sum", "$orderRevenue"))
                .append("totalUnits", new Document("$sum", "$orderUnits"))));
        return p;
    }
}
