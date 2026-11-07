package com.benchmark.nosqlblocking.cart;

import com.benchmark.nosqlblocking.domain.CartDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MongoDB cart store used when {@code app.cart.store=mongo}; exists so S2 can compare
 * cart operations on Redis against the same operations on the document database.
 */
@Component
@ConditionalOnProperty(name = "app.cart.store", havingValue = "mongo")
public class MongoCartStore implements CartStore {

    private static final String COLLECTION = "carts";

    private final MongoTemplate mongoTemplate;

    public MongoCartStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Map<String, Integer> items(String cartId) {
        CartDocument cart = mongoTemplate.findById(cartId, CartDocument.class, COLLECTION);
        if (cart == null || cart.getItems() == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(cart.getItems());
    }

    @Override
    public Map<String, Integer> addItem(String cartId, String productId, int quantity) {
        Map<String, Integer> items = items(cartId);
        items.merge(productId, quantity, Integer::sum);
        save(cartId, items);
        return items;
    }

    @Override
    public Map<String, Integer> updateItem(String cartId, String productId, int quantity) {
        Map<String, Integer> items = items(cartId);
        items.put(productId, quantity);
        save(cartId, items);
        return items;
    }

    @Override
    public Map<String, Integer> removeItem(String cartId, String productId) {
        Map<String, Integer> items = items(cartId);
        items.remove(productId);
        save(cartId, items);
        return items;
    }

    @Override
    public void clear(String cartId) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(cartId)), COLLECTION);
    }

    private void save(String cartId, Map<String, Integer> items) {
        if (items.isEmpty()) {
            clear(cartId);
            return;
        }
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(cartId)),
                new Update().set("items", new HashMap<>(items)).set("updatedAt", Instant.now()),
                COLLECTION);
    }
}
