package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.AdminDtos.ExplainResponse;
import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IndexAndExplainService {

    private static final Logger log = LoggerFactory.getLogger(IndexAndExplainService.class);

    private final MongoTemplate mongoTemplate;
    private volatile boolean indexesEnabled = false;

    public IndexAndExplainService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * The experiment indexes are managed only through the admin toggle (S4), never created implicitly,
     * so the starting state is derived from what actually exists in the collection.
     */
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
            log.info("[MongoDB] Creating experiment indexes for S4...");
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
            log.info("[MongoDB] Dropping experiment indexes for S4...");
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

    /**
     * Explains the queries that S4 actually runs (plus full-dataset contrasts), so the persisted
     * plan proves index usage on the measured workload — not on unrelated proxy queries.
     * Q1=catalog (category+price sort), Q2=sales-by-category in the S4 date window,
     * Q3=top-products in the S4 date window, Q4=sales-by-category full dataset (contrast),
     * Q5=top-products full dataset (contrast). Window matches {@code loadtests/s4/aggregation-plans.js}.
     */
    public ExplainResponse explain(String queryType) {
        MongoCollection<Document> products = mongoTemplate.getCollection("products");
        MongoCollection<Document> orders = mongoTemplate.getCollection("orders");
        Document planDoc;

        java.util.Date from = java.util.Date.from(java.time.Instant.parse("2024-01-05T00:00:00Z"));
        java.util.Date to = java.util.Date.from(java.time.Instant.parse("2024-01-05T06:00:00Z"));

        switch (queryType.toUpperCase()) {
            case "Q1" -> planDoc = products.find(new Document("category", "laptops"))
                    .sort(new Document("price", 1)).limit(20).explain();
            case "Q2" -> planDoc = orders.aggregate(salesByCategoryPipeline(from, to)).explain();
            case "Q3" -> planDoc = orders.aggregate(topProductsPipeline(from, to, 10)).explain();
            case "Q4" -> planDoc = orders.aggregate(salesByCategoryPipeline(null, null)).explain();
            case "Q5" -> planDoc = orders.aggregate(topProductsPipeline(null, null, 10)).explain();
            case "Q6" -> planDoc = orders.aggregate(orderTotalsPipeline(from, to)).explain();
            case "Q7" -> planDoc = orders.aggregate(orderTotalsPipeline(null, null)).explain();
            default -> throw new IllegalArgumentException("Unknown query for explain: " + queryType);
        }

        return ExplainResponse.builder()
                .query(queryType)
                .plan(planDoc.toJson())
                .build();
    }

    /** Mirrors {@code ReportService.getSalesByCategory} for an explainable pipeline. */
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

    /** Mirrors {@code ReportService.getTopProducts} for an explainable pipeline. */
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
