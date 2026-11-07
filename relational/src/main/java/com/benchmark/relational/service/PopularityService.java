package com.benchmark.relational.service;

import com.benchmark.common.dto.ReportDtos.PopularItemResponse;
import com.benchmark.relational.metrics.BenchmarkMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Popularity ranking backed by PostgreSQL. Views are kept in the variant-agnostic
 * {@code product_view_count} counter (upserted on every product read); purchases are derived
 * at read time from {@code order_item}. Both reads and writes are best effort so that a
 * failing counter never breaks the catalog path.
 */
@Service
public class PopularityService {

    private static final Logger log = LoggerFactory.getLogger(PopularityService.class);

    private final JdbcTemplate jdbcTemplate;
    private final BenchmarkMetrics metrics;

    public PopularityService(JdbcTemplate jdbcTemplate, BenchmarkMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    public void recordView(String productId) {
        metrics.timeVoid("postgres", "popularity.view", () -> {
            try {
                jdbcTemplate.update(
                        "INSERT INTO product_view_count (product_id, views) VALUES (?, 1) "
                                + "ON CONFLICT (product_id) DO UPDATE SET views = product_view_count.views + 1",
                        productId);
            } catch (RuntimeException ex) {
                log.warn("Skipping popularity.view for {} - counter unavailable: {}", productId, ex.getMessage());
            }
        });
    }

    /**
     * Purchases are not stored separately: {@code /api/popular?type=purchases} aggregates
     * {@code order_item} directly, so the order flow has nothing extra to persist here.
     */
    public void recordPurchase(String productId, int quantity) {
        // No-op by design; kept for parity with the order flow.
    }

    public List<PopularItemResponse> top(String type, int limit) {
        int capped = Math.max(limit, 1);
        return metrics.time("postgres", "popularity.top", () -> {
            try {
                String sql = "purchases".equalsIgnoreCase(type)
                        ? "SELECT product_id, SUM(quantity) AS score FROM order_item "
                                + "GROUP BY product_id ORDER BY score DESC LIMIT ?"
                        : "SELECT product_id, views AS score FROM product_view_count ORDER BY score DESC LIMIT ?";

                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, capped);
                List<PopularItemResponse> result = new ArrayList<>(rows.size());
                for (Map<String, Object> row : rows) {
                    Object score = row.get("score");
                    result.add(PopularItemResponse.builder()
                            .productId((String) row.get("product_id"))
                            .score(score instanceof Number number ? number.doubleValue() : 0.0)
                            .build());
                }
                return result;
            } catch (RuntimeException ex) {
                log.warn("Popularity ranking unavailable (PostgreSQL): {}", ex.getMessage());
                return List.of();
            }
        });
    }
}
