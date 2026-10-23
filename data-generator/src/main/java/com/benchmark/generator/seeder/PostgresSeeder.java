package com.benchmark.generator.seeder;

import com.benchmark.common.dto.OrderDtos;
import com.benchmark.generator.core.DataModelGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.*;

@Component
public class PostgresSeeder {

    private static final Logger log = LoggerFactory.getLogger(PostgresSeeder.class);
    private static final int BATCH_SIZE = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory cache of category and attribute definition IDs
    private final Map<String, String> categoryIds = new HashMap<>();
    private final Map<String, Map<String, String>> attrDefIds = new HashMap<>(); // category -> (attrName -> defId)

    public PostgresSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reset() {
        log.info("[PostgresSeeder] Truncating database tables...");
        jdbcTemplate.execute("TRUNCATE TABLE order_status_history, order_item, orders, cart_item, cart, review, product_attribute_value, product, product_jsonb CASCADE");
    }

    public void ensureCategoriesAndDefinitions() {
        for (String cat : DataModelGenerator.CATEGORIES) {
            String catId = "cat-" + cat;
            jdbcTemplate.update(
                    "INSERT INTO category (id, name, slug) VALUES (?, ?, ?) ON CONFLICT (name) DO UPDATE SET slug = EXCLUDED.slug",
                    catId, cat, cat
            );
            categoryIds.put(cat, catId);
            attrDefIds.putIfAbsent(cat, new HashMap<>());
        }

        // Define expected attributes per category
        Map<String, Map<String, String>> schema = Map.of(
                "laptops", Map.of("cpu", "string", "ramGb", "number", "storageGb", "number", "screenInch", "number", "gpu", "string", "weightKg", "number"),
                "phones", Map.of("os", "string", "ramGb", "number", "storageGb", "number", "batteryMah", "number", "cameraMp", "number", "fiveG", "boolean"),
                "clothing", Map.of("size", "string", "color", "string", "material", "string", "gender", "string", "season", "string"),
                "groceries", Map.of("weightG", "number", "organic", "boolean", "expiryDays", "number", "origin", "string", "allergens", "string"),
                "books", Map.of("author", "string", "pages", "number", "language", "string", "isbn", "string", "genre", "string")
        );

        for (var entry : schema.entrySet()) {
            String cat = entry.getKey();
            String catId = categoryIds.get(cat);
            for (var attrEntry : entry.getValue().entrySet()) {
                String attrName = attrEntry.getKey();
                String dataType = attrEntry.getValue();
                String defId = "attr-" + cat + "-" + attrName;

                jdbcTemplate.update(
                        "INSERT INTO attribute_definition (id, category_id, name, data_type) VALUES (?, ?, ?, ?) ON CONFLICT (category_id, name) DO NOTHING",
                        defId, catId, attrName, dataType
                );
                attrDefIds.get(cat).put(attrName, defId);
            }
        }
    }

    public void seedNormalized(List<DataModelGenerator.GeneratedProduct> products, List<OrderDtos.OrderResponse> orders) {
        ensureCategoriesAndDefinitions();
        log.info("[PostgresSeeder] Seeding normalized schema with {} products...", products.size());

        // 1. Insert products
        List<Object[]> productBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> attrValueBatch = new ArrayList<>(BATCH_SIZE * 5);
        List<Object[]> reviewBatch = new ArrayList<>(BATCH_SIZE * 5);

        for (int i = 0; i < products.size(); i++) {
            var gp = products.get(i);
            var p = gp.dto();
            String catId = categoryIds.get(p.getCategory());

            productBatch.add(new Object[]{
                    p.getId(), p.getSku(), p.getName(), catId, p.getPrice(), p.getStock(),
                    p.getReviewCount(), p.getRatingAvg(),
                    Timestamp.from(p.getCreatedAt()), Timestamp.from(p.getUpdatedAt())
            });

            // Attributes
            for (var attrEntry : gp.rawAttributes().entrySet()) {
                String attrName = attrEntry.getKey();
                Object val = attrEntry.getValue();
                String defId = attrDefIds.get(p.getCategory()).get(attrName);
                if (defId != null) {
                    String strVal = null;
                    Double numVal = null;
                    Boolean boolVal = null;

                    if (val instanceof Number n) {
                        numVal = n.doubleValue();
                    } else if (val instanceof Boolean b) {
                        boolVal = b;
                    } else if (val != null) {
                        strVal = val.toString();
                    }

                    attrValueBatch.add(new Object[]{
                            UUID.randomUUID().toString(), p.getId(), defId, strVal, numVal, boolVal
                    });
                }
            }

            // Reviews
            for (var r : p.getReviews()) {
                reviewBatch.add(new Object[]{
                        UUID.randomUUID().toString(), p.getId(), r.getRating(), r.getText(), Timestamp.from(r.getCreatedAt())
                });
            }

            if (productBatch.size() >= BATCH_SIZE || i == products.size() - 1) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO product (id, sku, name, category_id, price, stock, review_count, rating_avg, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        productBatch
                );
                jdbcTemplate.batchUpdate(
                        "INSERT INTO product_attribute_value (id, product_id, attribute_definition_id, string_value, number_value, boolean_value) VALUES (?, ?, ?, ?, ?, ?)",
                        attrValueBatch
                );
                if (!reviewBatch.isEmpty()) {
                    jdbcTemplate.batchUpdate(
                            "INSERT INTO review (id, product_id, rating, text, created_at) VALUES (?, ?, ?, ?, ?)",
                            reviewBatch
                    );
                }
                log.info("[PostgresSeeder] Inserted {} normalized products...", (i + 1));
                productBatch.clear();
                attrValueBatch.clear();
                reviewBatch.clear();
            }
        }

        // 2. Insert orders
        seedOrders(orders);
    }

    public void seedJsonb(List<DataModelGenerator.GeneratedProduct> products) {
        log.info("[PostgresSeeder] Seeding JSONB schema with {} products...", products.size());
        List<Object[]> jsonbBatch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < products.size(); i++) {
            var gp = products.get(i);
            var p = gp.dto();

            try {
                PGobject jsonbObj = new PGobject();
                jsonbObj.setType("jsonb");
                jsonbObj.setValue(objectMapper.writeValueAsString(gp.rawAttributes()));

                jsonbBatch.add(new Object[]{
                        p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getPrice(), p.getStock(),
                        jsonbObj, p.getReviewCount(), p.getRatingAvg(),
                        Timestamp.from(p.getCreatedAt()), Timestamp.from(p.getUpdatedAt())
                });
            } catch (Exception e) {
                throw new RuntimeException("JSON serialization error", e);
            }

            if (jsonbBatch.size() >= BATCH_SIZE || i == products.size() - 1) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO product_jsonb (id, sku, name, category, price, stock, attributes, review_count, rating_avg, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                                "ON CONFLICT (id) DO NOTHING",
                        jsonbBatch
                );
                log.info("[PostgresSeeder] Inserted {} JSONB products...", (i + 1));
                jsonbBatch.clear();
            }
        }
    }

    private void seedOrders(List<OrderDtos.OrderResponse> orders) {
        log.info("[PostgresSeeder] Seeding {} orders...", orders.size());
        List<Object[]> orderBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> itemBatch = new ArrayList<>(BATCH_SIZE * 3);
        List<Object[]> historyBatch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < orders.size(); i++) {
            var o = orders.get(i);
            orderBatch.add(new Object[]{
                    o.getId(), o.getCustomerRef(), o.getTotal(), o.getStatus(), Timestamp.from(o.getCreatedAt())
            });

            for (var it : o.getItems()) {
                itemBatch.add(new Object[]{
                        UUID.randomUUID().toString(), o.getId(), it.getProductId(), it.getProductName(), it.getPrice(), it.getQuantity()
                });
            }

            for (var h : o.getStatusHistory()) {
                historyBatch.add(new Object[]{
                        UUID.randomUUID().toString(), o.getId(), h.getStatus(), Timestamp.from(h.getTimestamp())
                });
            }

            if (orderBatch.size() >= BATCH_SIZE || i == orders.size() - 1) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO orders (id, customer_ref, total, status, created_at) VALUES (?, ?, ?, ?, ?)",
                        orderBatch
                );
                jdbcTemplate.batchUpdate(
                        "INSERT INTO order_item (id, order_id, product_id, product_name, price_snapshot, quantity) VALUES (?, ?, ?, ?, ?, ?)",
                        itemBatch
                );
                jdbcTemplate.batchUpdate(
                        "INSERT INTO order_status_history (id, order_id, status, created_at) VALUES (?, ?, ?, ?)",
                        historyBatch
                );
                log.info("[PostgresSeeder] Inserted {} orders...", (i + 1));
                orderBatch.clear();
                itemBatch.clear();
                historyBatch.clear();
            }
        }
    }
}
