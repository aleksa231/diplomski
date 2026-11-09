package com.benchmark.relational.service;

import com.benchmark.common.dto.ReportDtos.CategoryMonthlySummaryResponse;
import com.benchmark.common.dto.ReportDtos.CategoryRevenueDetailResponse;
import com.benchmark.common.dto.ReportDtos.CategorySalesResponse;
import com.benchmark.common.dto.ReportDtos.OrderTotalsResponse;
import com.benchmark.common.dto.ReportDtos.TopProductResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting queries (S7). Revenue and ranking reports join {@code orders}/{@code order_item}
 * with {@code product}/{@code category}; the monthly summary uses CTEs so that the join-heavy
 * aggregation stays inside PostgreSQL.
 */
@Service
public class ReportService {

    private final JdbcTemplate jdbcTemplate;

    public ReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CategorySalesResponse> getSalesByCategory(Instant from, Instant to) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.name AS category, COUNT(DISTINCT o.id) AS order_count, "
                        + "COALESCE(SUM(oi.price_snapshot * oi.quantity), 0) AS total_revenue "
                        + "FROM orders o "
                        + "JOIN order_item oi ON oi.order_id = o.id "
                        + "JOIN product p ON p.id = oi.product_id "
                        + "JOIN category c ON c.id = p.category_id");
        List<Object> params = new ArrayList<>();
        appendDateRange(sql, params, "o.created_at", from, to);
        sql.append(" GROUP BY c.name ORDER BY total_revenue DESC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> CategorySalesResponse.builder()
                .category(rs.getString("category"))
                .orderCount(rs.getLong("order_count"))
                .totalRevenue(round2(rs.getDouble("total_revenue")))
                .build(), params.toArray());
    }

    public List<TopProductResponse> getTopProducts(Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT oi.product_id AS product_id, oi.product_name AS product_name, c.name AS category, "
                        + "SUM(oi.quantity) AS units_sold, SUM(oi.price_snapshot * oi.quantity) AS revenue "
                        + "FROM orders o "
                        + "JOIN order_item oi ON oi.order_id = o.id "
                        + "JOIN product p ON p.id = oi.product_id "
                        + "JOIN category c ON c.id = p.category_id");
        List<Object> params = new ArrayList<>();
        appendDateRange(sql, params, "o.created_at", from, to);
        sql.append(" GROUP BY oi.product_id, oi.product_name, c.name ORDER BY units_sold DESC LIMIT ?");
        params.add(limit > 0 ? limit : 10);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> TopProductResponse.builder()
                .productId(rs.getString("product_id"))
                .productName(rs.getString("product_name"))
                .category(rs.getString("category"))
                .unitsSold(rs.getLong("units_sold"))
                .revenue(round2(rs.getDouble("revenue")))
                .build(), params.toArray());
    }

    /**
     * For every category and month returns revenue, the number of distinct orders, the average
     * basket size and the top 3 products by units sold. One CTE expands the order lines (resolving
     * the category through the product), a second aggregates per category/month/order and a third
     * per category/month/product; the two aggregates are stitched together in memory.
     */
    public List<CategoryMonthlySummaryResponse> getCategoryMonthlySummary() {
        String lineCte =
                "WITH line AS ("
                        + " SELECT c.name AS category, o.id AS order_id, oi.product_id AS product_id, "
                        + "        oi.product_name AS product_name, oi.quantity AS quantity, "
                        + "        (oi.price_snapshot * oi.quantity) AS line_revenue, "
                        + "        EXTRACT(YEAR FROM o.created_at) AS yr, EXTRACT(MONTH FROM o.created_at) AS mo"
                        + " FROM orders o"
                        + " JOIN order_item oi ON oi.order_id = o.id"
                        + " JOIN product p ON p.id = oi.product_id"
                        + " JOIN category c ON c.id = p.category_id)";

        String summarySql = lineCte
                + " SELECT category, yr, mo, SUM(line_revenue) AS revenue, SUM(quantity) AS total_items, "
                + "        COUNT(DISTINCT order_id) AS order_count"
                + " FROM line GROUP BY category, yr, mo ORDER BY yr DESC, mo DESC, category ASC";

        String productSql = lineCte
                + " SELECT category, yr, mo, product_id, product_name, SUM(quantity) AS units, "
                + "        SUM(line_revenue) AS revenue"
                + " FROM line GROUP BY category, yr, mo, product_id, product_name"
                + " ORDER BY yr DESC, mo DESC, category ASC, units DESC";

        List<Map<String, Object>> summaries = jdbcTemplate.queryForList(summarySql);
        List<Map<String, Object>> products = jdbcTemplate.queryForList(productSql);

        Map<String, List<Map<String, Object>>> productsByGroup = new LinkedHashMap<>();
        for (Map<String, Object> product : products) {
            productsByGroup.computeIfAbsent(groupKey(product), k -> new ArrayList<>()).add(product);
        }

        List<CategoryMonthlySummaryResponse> result = new ArrayList<>(summaries.size());
        for (Map<String, Object> summary : summaries) {
            long orderCount = asLong(summary.get("order_count"));
            long totalItems = asLong(summary.get("total_items"));
            double averageBasketSize = orderCount == 0
                    ? 0.0
                    : Math.round(((double) totalItems / orderCount) * 10.0) / 10.0;

            List<TopProductResponse> topProducts = productsByGroup
                    .getOrDefault(groupKey(summary), List.of()).stream()
                    .limit(3)
                    .map(product -> TopProductResponse.builder()
                            .productId((String) product.get("product_id"))
                            .productName((String) product.get("product_name"))
                            .category((String) product.get("category"))
                            .unitsSold(asLong(product.get("units")))
                            .revenue(round2(asDouble(product.get("revenue"))))
                            .build())
                    .toList();

            result.add(CategoryMonthlySummaryResponse.builder()
                    .category((String) summary.get("category"))
                    .year((int) asLong(summary.get("yr")))
                    .month((int) asLong(summary.get("mo")))
                    .revenue(round2(asDouble(summary.get("revenue"))))
                    .orderCount(orderCount)
                    .averageBasketSize(averageBasketSize)
                    .topProducts(topProducts)
                    .build());
        }
        return result;
    }

    private static void appendDateRange(StringBuilder sql, List<Object> params, String column, Instant from, Instant to) {
        boolean hasWhere = false;
        if (from != null) {
            sql.append(" WHERE ").append(column).append(" >= ?");
            params.add(Timestamp.from(from));
            hasWhere = true;
        }
        if (to != null) {
            sql.append(hasWhere ? " AND " : " WHERE ").append(column).append(" <= ?");
            params.add(Timestamp.from(to));
        }
    }

    /**
     * S5 - where the relational model wins. A single multi-join analytical query:
     * orders → order_item → product → category, grouped by (category, month), computing
     * revenue, distinct orders/customers/products, and averages. PostgreSQL executes this
     * with hash/merge joins and native aggregate functions in one pass.
     */
    public List<CategoryRevenueDetailResponse> getCategoryRevenueDetail(Instant from, Instant to) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.name AS category, "
                        + "EXTRACT(YEAR FROM o.created_at)::int AS yr, "
                        + "EXTRACT(MONTH FROM o.created_at)::int AS mo, "
                        + "COUNT(DISTINCT o.id) AS order_count, "
                        + "COUNT(DISTINCT o.customer_ref) AS customer_count, "
                        + "COALESCE(SUM(oi.quantity), 0) AS units_sold, "
                        + "COALESCE(SUM(oi.price_snapshot * oi.quantity), 0) AS revenue, "
                        + "COUNT(DISTINCT oi.product_id) AS distinct_products, "
                        + "COALESCE(AVG(oi.price_snapshot), 0) AS avg_price "
                        + "FROM orders o "
                        + "JOIN order_item oi ON oi.order_id = o.id "
                        + "JOIN product p ON p.id = oi.product_id "
                        + "JOIN category c ON c.id = p.category_id");
        List<Object> params = new ArrayList<>();
        appendDateRange(sql, params, "o.created_at", from, to);
        sql.append(" GROUP BY c.name, EXTRACT(YEAR FROM o.created_at), EXTRACT(MONTH FROM o.created_at)")
           .append(" ORDER BY yr DESC, mo DESC, revenue DESC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> CategoryRevenueDetailResponse.builder()
                .category(rs.getString("category"))
                .year(rs.getInt("yr"))
                .month(rs.getInt("mo"))
                .orderCount(rs.getLong("order_count"))
                .customerCount(rs.getLong("customer_count"))
                .unitsSold(rs.getLong("units_sold"))
                .revenue(round2(rs.getDouble("revenue")))
                .distinctProducts(rs.getLong("distinct_products"))
                .averagePrice(round2(rs.getDouble("avg_price")))
                .build(), params.toArray());
    }

    private static String groupKey(Map<String, Object> row) {
        return row.get("category") + "|" + asLong(row.get("yr")) + "|" + asLong(row.get("mo"));
    }

    /**
     * Date-range totals joining only {@code orders}+{@code order_item} (no product/category). The
     * {@code created_at} filter on {@code orders} is the indexed step this isolates for S4B.
     */
    public OrderTotalsResponse getOrderTotals(Instant from, Instant to) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(DISTINCT o.id) AS order_count, "
                        + "COALESCE(SUM(oi.price_snapshot * oi.quantity), 0) AS total_revenue, "
                        + "COALESCE(SUM(oi.quantity), 0) AS total_units "
                        + "FROM orders o JOIN order_item oi ON oi.order_id = o.id");
        List<Object> params = new ArrayList<>();
        appendDateRange(sql, params, "o.created_at", from, to);
        return jdbcTemplate.query(sql.toString(), rs -> {
            if (rs.next()) {
                return OrderTotalsResponse.builder()
                        .orderCount(rs.getLong("order_count"))
                        .totalRevenue(round2(rs.getDouble("total_revenue")))
                        .totalUnits(rs.getLong("total_units"))
                        .build();
            }
            return OrderTotalsResponse.builder().orderCount(0L).totalRevenue(0.0).totalUnits(0L).build();
        }, params.toArray());
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
