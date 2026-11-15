package com.benchmark.relational.service;

import com.benchmark.common.dto.AdminDtos.ExplainResponse;
import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Index toggle and query plan inspection for the relational model.
 */
@Service
public class IndexAndExplainService {

    private static final Logger log = LoggerFactory.getLogger(IndexAndExplainService.class);

    private static final String NORMALIZED_CAT_PRICE_INDEX = "idx_exp_product_cat_price";
    private static final String NORMALIZED_ATTR_INDEX = "idx_exp_pav_def_number";
    private static final String ORDER_ITEM_INDEX = "idx_exp_order_item_product";
    private static final String ORDER_DATE_INDEX = "idx_exp_order_created";
    private static final String BASELINE_PRODUCT_CAT_INDEX = "idx_product_cat";

    private static final String S4_FROM = "2024-01-05 00:00:00+00";
    private static final String S4_TO = "2024-01-05 06:00:00+00";

    private final JdbcTemplate jdbcTemplate;

    private volatile boolean indexesEnabled = false;

    public IndexAndExplainService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void detectIndexState() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?", Integer.class, NORMALIZED_CAT_PRICE_INDEX);
            this.indexesEnabled = count != null && count > 0;
        } catch (Exception ex) {
            log.warn("[PostgreSQL] Unable to inspect experiment indexes: {}", ex.getMessage());
        }
    }

    public boolean isIndexesEnabled() {
        return indexesEnabled;
    }

    public synchronized IndexToggleResponse toggleIndexes(boolean enabled) {
        if (enabled) {
            log.info("[PostgreSQL] Creating indexes...");
            createIndexes();
            this.indexesEnabled = true;
            return IndexToggleResponse.builder()
                    .indexesEnabled(true)
                    .message("Experiment indexes created in PostgreSQL")
                    .build();
        }
        log.info("[PostgreSQL] Dropping indexes...");
        dropIndexes();
        this.indexesEnabled = false;
        return IndexToggleResponse.builder()
                .indexesEnabled(false)
                .message("Experiment indexes dropped from PostgreSQL")
                .build();
    }

    public ExplainResponse explain(String queryType) {
        String sql = switch (queryType.toUpperCase()) {
            case "Q1" -> q1();
            case "Q2" -> q2();
            case "Q3" -> q3();
            case "Q4" -> q4();
            case "Q5" -> q5();
            case "Q6" -> q6();
            case "Q7" -> q7();
            default -> throw new IllegalArgumentException("Unknown query for explain: " + queryType);
        };

        List<String> plan = jdbcTemplate.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, String.class);
        return ExplainResponse.builder()
                .query(queryType)
                .plan(String.join("\n", plan))
                .build();
    }

    private void createIndexes() {
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + NORMALIZED_CAT_PRICE_INDEX
                + " ON product (category_id, price)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + NORMALIZED_ATTR_INDEX
                + " ON product_attribute_value (attribute_definition_id, number_value)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + ORDER_ITEM_INDEX
                + " ON order_item (product_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + ORDER_DATE_INDEX
                + " ON orders (created_at)");
    }

    private void dropIndexes() {
        for (String index : List.of(NORMALIZED_CAT_PRICE_INDEX, NORMALIZED_ATTR_INDEX,
                ORDER_ITEM_INDEX, ORDER_DATE_INDEX, BASELINE_PRODUCT_CAT_INDEX)) {
            jdbcTemplate.execute("DROP INDEX IF EXISTS " + index);
        }
    }

    private String q1() {
        return "SELECT p.id, p.name, p.price FROM product p "
                + "JOIN category c ON p.category_id = c.id "
                + "WHERE c.name = 'laptops' ORDER BY p.price ASC LIMIT 20";
    }

    private String q2() {
        return "SELECT c.name AS category, COUNT(DISTINCT o.id) AS order_count, "
                + "COALESCE(SUM(oi.price_snapshot * oi.quantity), 0) AS total_revenue "
                + "FROM orders o "
                + "JOIN order_item oi ON oi.order_id = o.id "
                + "JOIN product p ON p.id = oi.product_id "
                + "JOIN category c ON c.id = p.category_id "
                + "WHERE o.created_at >= TIMESTAMPTZ '" + S4_FROM + "' "
                + "AND o.created_at <= TIMESTAMPTZ '" + S4_TO + "' "
                + "GROUP BY c.name ORDER BY total_revenue DESC";
    }

    private String q3() {
        return "SELECT oi.product_id, oi.product_name, c.name AS category, "
                + "SUM(oi.quantity) AS units_sold, SUM(oi.price_snapshot * oi.quantity) AS revenue "
                + "FROM orders o "
                + "JOIN order_item oi ON oi.order_id = o.id "
                + "JOIN product p ON p.id = oi.product_id "
                + "JOIN category c ON c.id = p.category_id "
                + "WHERE o.created_at >= TIMESTAMPTZ '" + S4_FROM + "' "
                + "AND o.created_at <= TIMESTAMPTZ '" + S4_TO + "' "
                + "GROUP BY oi.product_id, oi.product_name, c.name ORDER BY units_sold DESC LIMIT 10";
    }

    private String q4() {
        return "SELECT c.name AS category, EXTRACT(YEAR FROM o.created_at) AS yr, "
                + "EXTRACT(MONTH FROM o.created_at) AS mo, "
                + "COALESCE(SUM(oi.price_snapshot * oi.quantity), 0) AS revenue "
                + "FROM orders o "
                + "JOIN order_item oi ON oi.order_id = o.id "
                + "JOIN product p ON p.id = oi.product_id "
                + "JOIN category c ON c.id = p.category_id "
                + "GROUP BY c.name, yr, mo ORDER BY yr DESC, mo DESC";
    }

    private String q5() {
        return "SELECT oi.product_id, SUM(oi.quantity) AS units FROM order_item oi "
                + "GROUP BY oi.product_id ORDER BY units DESC LIMIT 10";
    }

    private String q6() {
        return "SELECT COUNT(DISTINCT o.id) AS order_count, "
                + "COALESCE(SUM(oi.price_snapshot * oi.quantity), 0) AS total_revenue, "
                + "COALESCE(SUM(oi.quantity), 0) AS total_units "
                + "FROM orders o JOIN order_item oi ON oi.order_id = o.id "
                + "WHERE o.created_at >= TIMESTAMPTZ '" + S4_FROM + "' "
                + "AND o.created_at <= TIMESTAMPTZ '" + S4_TO + "'";
    }

    private String q7() {
        return "SELECT COUNT(DISTINCT o.id) AS order_count, "
                + "COALESCE(SUM(oi.price_snapshot * oi.quantity), 0) AS total_revenue, "
                + "COALESCE(SUM(oi.quantity), 0) AS total_units "
                + "FROM orders o JOIN order_item oi ON oi.order_id = o.id";
    }
}
