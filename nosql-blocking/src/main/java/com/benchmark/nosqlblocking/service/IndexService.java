package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Manages the experiment indexes through the admin toggle. Indexes are never created implicitly,
 * so the starting state is derived from what actually exists in the collection.
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    private final MongoTemplate mongoTemplate;
    private volatile boolean indexesEnabled = false;

    public IndexService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    void detectIndexState() {
        this.indexesEnabled = textIndexExists();
    }

    public boolean isIndexesEnabled() {
        return indexesEnabled;
    }

    private boolean textIndexExists() {
        try {
            for (Document index : mongoTemplate.getCollection("products").listIndexes()) {
                Document key = index.get("key", Document.class);
                if (key != null && "text".equals(key.getString("name"))) {
                    return true;
                }
            }
        } catch (Exception ex) {
            log.warn("[MongoDB] Unable to inspect product indexes: {}", ex.getMessage());
        }
        return false;
    }

    public synchronized IndexToggleResponse toggleIndexes(boolean enabled) {
        MongoCollection<Document> products = mongoTemplate.getCollection("products");
        MongoCollection<Document> orders = mongoTemplate.getCollection("orders");

        if (enabled) {
            log.info("[MongoDB] Creating experiment indexes...");
            products.createIndex(Indexes.ascending("category", "price"));
            products.createIndex(Indexes.ascending("category", "attributes.ramGb"));
            products.createIndex(Indexes.text("name"));
            orders.createIndex(Indexes.ascending("createdAt"));
            orders.createIndex(Indexes.ascending("items.productId"));
            this.indexesEnabled = true;
            return IndexToggleResponse.builder()
                    .indexesEnabled(true)
                    .message("Experiment indexes created in MongoDB")
                    .build();
        } else {
            log.info("[MongoDB] Dropping experiment indexes...");
            try {
                products.dropIndex("category_1_price_1");
            } catch (Exception ignored) {}
            try {
                products.dropIndex("category_1_attributes.ramGb_1");
            } catch (Exception ignored) {}
            try {
                products.dropIndex("name_text");
            } catch (Exception ignored) {}
            try {
                orders.dropIndex("createdAt_1");
            } catch (Exception ignored) {}
            try {
                orders.dropIndex("items.productId_1");
            } catch (Exception ignored) {}
            this.indexesEnabled = false;
            return IndexToggleResponse.builder()
                    .indexesEnabled(false)
                    .message("Experiment indexes dropped from MongoDB")
                    .build();
        }
    }
}
