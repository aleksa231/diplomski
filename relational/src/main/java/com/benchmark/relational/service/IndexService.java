package com.benchmark.relational.service;

import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Manages the experiment indexes of the relational model through the admin toggle.
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    private static final String NORMALIZED_CAT_PRICE_INDEX = "idx_exp_product_cat_price";
    private static final String NORMALIZED_ATTR_INDEX = "idx_exp_pav_def_number";
    private static final String ORDER_ITEM_INDEX = "idx_exp_order_item_product";
    private static final String ORDER_DATE_INDEX = "idx_exp_order_created";
    private static final String BASELINE_PRODUCT_CAT_INDEX = "idx_product_cat";

    private final JdbcTemplate jdbcTemplate;

    private volatile boolean indexesEnabled = false;

    public IndexService(JdbcTemplate jdbcTemplate) {
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
}
