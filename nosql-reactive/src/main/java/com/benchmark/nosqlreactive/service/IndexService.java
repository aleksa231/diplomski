package com.benchmark.nosqlreactive.service;

import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import com.mongodb.client.model.Indexes;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reactor-native counterpart of the blocking {@code IndexService}: index state detection and the
 * admin toggle, all driven through the reactive MongoDB driver ({@code listIndexes},
 * {@code createIndex}, {@code dropIndex}).
 *
 * <p>Indexes are managed only through the admin toggle, never created implicitly, so the starting
 * state is derived from what actually exists in the collection. Because a {@code @PostConstruct}
 * hook cannot await a reactive call, the detection is resolved lazily on the first read of
 * {@link #indexesEnabled()} and cached afterwards (the toggle also marks the state as resolved).
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);
    private static final String PRODUCTS = "products";
    private static final String ORDERS = "orders";

    private final ReactiveMongoTemplate mongoTemplate;
    private final AtomicBoolean indexesEnabled = new AtomicBoolean(false);
    private final AtomicBoolean indexStateResolved = new AtomicBoolean(false);

    public IndexService(ReactiveMongoTemplate mongoTemplate) {
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

    private Mono<IndexToggleResponse> createIndexes(MongoCollection<Document> products,
                                                    MongoCollection<Document> orders) {
        log.info("[MongoDB] Creating experiment indexes...");
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
        log.info("[MongoDB] Dropping experiment indexes...");
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
}
