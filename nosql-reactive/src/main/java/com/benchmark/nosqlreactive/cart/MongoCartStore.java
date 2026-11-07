package com.benchmark.nosqlreactive.cart;

import com.benchmark.nosqlreactive.domain.CartDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MongoDB cart store used when {@code app.cart.store=mongo}; exists so S2 can compare
 * cart operations on Redis against the same operations on the document database.
 */
@Component
@ConditionalOnProperty(name = "app.cart.store", havingValue = "mongo")
public class MongoCartStore implements CartStore {

    private static final String COLLECTION = "carts";

    private final ReactiveMongoTemplate mongoTemplate;

    public MongoCartStore(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Mono<Map<String, Integer>> items(String cartId) {
        return mongoTemplate.findById(cartId, CartDocument.class, COLLECTION)
                .map(CartDocument::getItems)
                .filter(Objects::nonNull)
                .map(items -> (Map<String, Integer>) new LinkedHashMap<>(items))
                .defaultIfEmpty(new LinkedHashMap<>());
    }

    @Override
    public Mono<Map<String, Integer>> addItem(String cartId, String productId, int quantity) {
        return items(cartId).flatMap(current -> {
            Map<String, Integer> items = new LinkedHashMap<>(current);
            items.merge(productId, quantity, Integer::sum);
            return save(cartId, items).thenReturn(items);
        });
    }

    @Override
    public Mono<Map<String, Integer>> updateItem(String cartId, String productId, int quantity) {
        return items(cartId).flatMap(current -> {
            Map<String, Integer> items = new LinkedHashMap<>(current);
            items.put(productId, quantity);
            return save(cartId, items).thenReturn(items);
        });
    }

    @Override
    public Mono<Map<String, Integer>> removeItem(String cartId, String productId) {
        return items(cartId).flatMap(current -> {
            Map<String, Integer> items = new LinkedHashMap<>(current);
            items.remove(productId);
            return save(cartId, items).thenReturn(items);
        });
    }

    @Override
    public Mono<Void> clear(String cartId) {
        return mongoTemplate.remove(Query.query(Criteria.where("_id").is(cartId)), COLLECTION).then();
    }

    private Mono<Void> save(String cartId, Map<String, Integer> items) {
        if (items.isEmpty()) {
            return clear(cartId);
        }
        return mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(cartId)),
                new Update().set("items", new HashMap<>(items)).set("updatedAt", Instant.now()),
                COLLECTION).then();
    }
}
