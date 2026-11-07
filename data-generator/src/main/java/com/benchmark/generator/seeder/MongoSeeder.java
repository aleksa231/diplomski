package com.benchmark.generator.seeder;

import com.benchmark.common.dto.OrderDtos;
import com.benchmark.generator.core.DataModelGenerator;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class MongoSeeder {

    private static final Logger log = LoggerFactory.getLogger(MongoSeeder.class);
    private static final int BATCH_SIZE = 2000;

    private final MongoTemplate mongoTemplate;

    public MongoSeeder(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void reset() {
        log.info("[MongoSeeder] Dropping products and orders collections...");
        mongoTemplate.dropCollection("products");
        mongoTemplate.dropCollection("orders");
    }

    public void seedProducts(List<DataModelGenerator.GeneratedProduct> products) {
        log.info("[MongoSeeder] Seeding {} products in batches of {}...", products.size(), BATCH_SIZE);
        List<Document> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < products.size(); i++) {
            var gp = products.get(i);
            var p = gp.dto();

            Document doc = new Document("_id", p.getId())
                    .append("sku", p.getSku())
                    .append("name", p.getName())
                    .append("category", p.getCategory())
                    .append("price", p.getPrice())
                    .append("stock", p.getStock())
                    .append("attributes", new Document(gp.rawAttributes()))
                    .append("reviewCount", p.getReviewCount())
                    .append("ratingAvg", p.getRatingAvg())
                    .append("createdAt", Date.from(p.getCreatedAt()))
                    .append("updatedAt", Date.from(p.getUpdatedAt()));

            List<Document> reviewDocs = new ArrayList<>();
            for (var r : p.getReviews()) {
                reviewDocs.add(new Document("rating", r.getRating())
                        .append("text", r.getText())
                        .append("createdAt", Date.from(r.getCreatedAt())));
            }
            doc.append("reviews", reviewDocs);
            batch.add(doc);

            if (batch.size() >= BATCH_SIZE || i == products.size() - 1) {
                mongoTemplate.insert(batch, "products");
                log.info("[MongoSeeder] Inserted {} products...", (i + 1));
                batch.clear();
            }
        }
    }

    public void seedOrders(List<OrderDtos.OrderResponse> orders) {
        log.info("[MongoSeeder] Seeding {} orders in batches of {}...", orders.size(), BATCH_SIZE);
        List<Document> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < orders.size(); i++) {
            var o = orders.get(i);
            List<Document> itemDocs = new ArrayList<>();
            for (var it : o.getItems()) {
                itemDocs.add(new Document("productId", it.getProductId())
                        .append("productName", it.getProductName())
                        .append("price", it.getPrice())
                        .append("quantity", it.getQuantity()));
            }

            List<Document> historyDocs = new ArrayList<>();
            for (var h : o.getStatusHistory()) {
                historyDocs.add(new Document("status", h.getStatus())
                        .append("timestamp", Date.from(h.getTimestamp())));
            }

            Document doc = new Document("_id", o.getId())
                    .append("customerRef", o.getCustomerRef())
                    .append("total", o.getTotal())
                    .append("status", o.getStatus())
                    .append("items", itemDocs)
                    .append("statusHistory", historyDocs)
                    .append("createdAt", Date.from(o.getCreatedAt()));

            batch.add(doc);

            if (batch.size() >= BATCH_SIZE || i == orders.size() - 1) {
                mongoTemplate.insert(batch, "orders");
                log.info("[MongoSeeder] Inserted {} orders...", (i + 1));
                batch.clear();
            }
        }
    }
}
