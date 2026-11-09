package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.ReportDtos.*;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class ReportService {

    private final MongoTemplate mongoTemplate;

    public ReportService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<CategorySalesResponse> getSalesByCategory(Instant from, Instant to) {
        List<AggregationOperation> ops = new ArrayList<>();

        if (from != null || to != null) {
            Criteria criteria = Criteria.where("createdAt");
            if (from != null) criteria = criteria.gte(Date.from(from));
            if (to != null) criteria = criteria.lte(Date.from(to));
            ops.add(match(criteria));
        }

        ops.add(unwind("items"));
        ops.add(lookup("products", "items.productId", "_id", "prod"));
        ops.add(unwind("prod"));

        ops.add(group("prod.category")
                .addToSet("_id").as("orderIds")
                .sum(ArithmeticOperators.Multiply.valueOf("items.price").multiplyBy("items.quantity")).as("totalRevenue"));

        ops.add(project("totalRevenue")
                .and("_id").as("category")
                .and(ArrayOperators.Size.lengthOfArray("orderIds")).as("orderCount"));

        ops.add(sort(Sort.Direction.DESC, "totalRevenue"));

        Aggregation agg = newAggregation(ops);
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "orders", Document.class);

        return results.getMappedResults().stream().map(d -> CategorySalesResponse.builder()
                .category(d.getString("category"))
                .orderCount(d.get("orderCount") instanceof Number n ? n.longValue() : 0L)
                .totalRevenue(d.get("totalRevenue") instanceof Number n ? Math.round(n.doubleValue() * 100.0) / 100.0 : 0.0)
                .build()).toList();
    }

    public List<TopProductResponse> getTopProducts(Instant from, Instant to, int limit) {
        List<AggregationOperation> ops = new ArrayList<>();

        if (from != null || to != null) {
            Criteria criteria = Criteria.where("createdAt");
            if (from != null) criteria = criteria.gte(Date.from(from));
            if (to != null) criteria = criteria.lte(Date.from(to));
            ops.add(match(criteria));
        }

        ops.add(unwind("items"));
        ops.add(lookup("products", "items.productId", "_id", "prod"));
        ops.add(unwind("prod"));

        ops.add(group("items.productId")
                .first("items.productName").as("productName")
                .first("prod.category").as("category")
                .sum("items.quantity").as("unitsSold")
                .sum(ArithmeticOperators.Multiply.valueOf("items.price").multiplyBy("items.quantity")).as("revenue"));

        ops.add(project("productName", "category", "unitsSold", "revenue")
                .and("_id").as("productId"));

        ops.add(sort(Sort.Direction.DESC, "unitsSold"));
        ops.add(limit(limit > 0 ? limit : 10));

        Aggregation agg = newAggregation(ops);
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "orders", Document.class);

        return results.getMappedResults().stream().map(d -> TopProductResponse.builder()
                .productId(d.getString("productId"))
                .productName(d.getString("productName"))
                .category(d.getString("category"))
                .unitsSold(d.get("unitsSold") instanceof Number n ? n.longValue() : 0L)
                .revenue(d.get("revenue") instanceof Number n ? Math.round(n.doubleValue() * 100.0) / 100.0 : 0.0)
                .build()).toList();
    }

    /**
     * Join-heavy report (S7): for every category and month it returns revenue, the number of distinct
     * orders, the average basket size and the top 3 products by units sold.
     *
     * <p>Stages: unwind the embedded order items, join a product to resolve the category, split the ISO
     * date into year/month, group once per order to keep the order count correct, then group per
     * category/month and collect the product lines. The top-3 selection is done on the small collected
     * array; all of the join/group work stays inside the database.
     */
    public List<CategoryMonthlySummaryResponse> getCategoryMonthlySummary() {
        List<AggregationOperation> ops = new ArrayList<>();
        ops.add(unwind("items"));
        ops.add(lookup("products", "items.productId", "_id", "prod"));
        ops.add(unwind("prod"));

        ops.add(project()
                .and("prod.category").as("category")
                .and("_id").as("orderId")
                .and("items.productId").as("productId")
                .and("items.productName").as("productName")
                .and("items.quantity").as("quantity")
                .and(ArithmeticOperators.Multiply.valueOf("items.price").multiplyBy("items.quantity")).as("lineRevenue")
                .and(DateOperators.DateToString.dateOf("createdAt").toString("%Y")).as("yearStr")
                .and(DateOperators.DateToString.dateOf("createdAt").toString("%m")).as("monthStr"));

        // One row per (category, month, order): the order's revenue, item count and its product lines.
        ops.add(group("category", "yearStr", "monthStr", "orderId")
                .sum("lineRevenue").as("orderRevenue")
                .sum("quantity").as("orderItems")
                .push(new Document("productId", "$productId")
                        .append("productName", "$productName")
                        .append("quantity", "$quantity")
                        .append("lineRevenue", "$lineRevenue"))
                .as("lines"));

        // One row per (category, month): the number of orders is the number of rows in the previous stage.
        ops.add(group("_id.category", "_id.yearStr", "_id.monthStr")
                .sum("orderRevenue").as("revenue")
                .sum("orderItems").as("totalItems")
                .count().as("orderCount")
                .push("lines").as("lineGroups"));

        Aggregation agg = newAggregation(ops);
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "orders", Document.class);

        List<CategoryMonthlySummaryResponse> summaries = new ArrayList<>();
        for (Document d : results.getMappedResults()) {
            Document id = d.get("_id", Document.class);
            String category = id.getString("category");
            long orderCount = asLong(d.get("orderCount"));
            long totalItems = asLong(d.get("totalItems"));
            double averageBasketSize = orderCount == 0
                    ? 0.0
                    : Math.round(((double) totalItems / orderCount) * 10.0) / 10.0;

            summaries.add(CategoryMonthlySummaryResponse.builder()
                    .category(category)
                    .year(Integer.parseInt(id.getString("yearStr")))
                    .month(Integer.parseInt(id.getString("monthStr")))
                    .revenue(round2(asDouble(d.get("revenue"))))
                    .orderCount(orderCount)
                    .averageBasketSize(averageBasketSize)
                    .topProducts(topProducts(category, d.get("lineGroups")))
                    .build());
        }

        summaries.sort(Comparator.comparing(CategoryMonthlySummaryResponse::getYear).reversed()
                .thenComparing(CategoryMonthlySummaryResponse::getMonth, Comparator.reverseOrder())
                .thenComparing(CategoryMonthlySummaryResponse::getCategory));
        return summaries;
    }

    /** Aggregates the collected per-order product lines into the top 3 products of a category/month. */
    @SuppressWarnings("unchecked")
    private List<TopProductResponse> topProducts(String category, Object lineGroups) {
        if (!(lineGroups instanceof List<?> groups)) {
            return new ArrayList<>();
        }

        Map<String, long[]> unitsByProduct = new LinkedHashMap<>();
        Map<String, double[]> revenueByProduct = new LinkedHashMap<>();
        Map<String, String> nameByProduct = new LinkedHashMap<>();

        for (Object group : groups) {
            if (!(group instanceof List<?> lines)) {
                continue;
            }
            for (Object line : lines) {
                if (!(line instanceof Document l)) {
                    continue;
                }
                String productId = l.getString("productId");
                if (productId == null) {
                    continue;
                }
                nameByProduct.putIfAbsent(productId, l.getString("productName"));
                unitsByProduct.computeIfAbsent(productId, k -> new long[1])[0] += asLong(l.get("quantity"));
                revenueByProduct.computeIfAbsent(productId, k -> new double[1])[0] += asDouble(l.get("lineRevenue"));
            }
        }

        return unitsByProduct.entrySet().stream()
                .sorted(Map.Entry.<String, long[]>comparingByValue(Comparator.comparingLong(a -> a[0])).reversed())
                .limit(3)
                .map(e -> TopProductResponse.builder()
                        .productId(e.getKey())
                        .productName(nameByProduct.get(e.getKey()))
                        .category(category)
                        .unitsSold(e.getValue()[0])
                        .revenue(round2(revenueByProduct.get(e.getKey())[0]))
                        .build())
                .toList();
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * S5 - where the relational model wins. Same analytical report as the relational implementation:
     * join orders→items→products (to resolve category), group by (category, month), compute
     * revenue and distinct counts. In MongoDB this requires $unwind + $lookup + multiple $addToSet
     * accumulators, which is the natural cost of this join-heavy analytical shape.
     */
    public List<CategoryRevenueDetailResponse> getCategoryRevenueDetail(Instant from, Instant to) {
        List<AggregationOperation> ops = new ArrayList<>();

        if (from != null || to != null) {
            Criteria criteria = Criteria.where("createdAt");
            if (from != null) criteria = criteria.gte(Date.from(from));
            if (to != null) criteria = criteria.lte(Date.from(to));
            ops.add(match(criteria));
        }

        ops.add(unwind("items"));
        ops.add(lookup("products", "items.productId", "_id", "prod"));
        ops.add(unwind("prod"));

        ops.add(project()
                .and("prod.category").as("category")
                .and("_id").as("orderId")
                .and("customerRef").as("customerRef")
                .and("items.productId").as("productId")
                .and("items.quantity").as("quantity")
                .and("items.price").as("price")
                .and(ArithmeticOperators.Multiply.valueOf("items.price").multiplyBy("items.quantity")).as("lineRevenue")
                .and(DateOperators.DateToString.dateOf("createdAt").toString("%Y")).as("yearStr")
                .and(DateOperators.DateToString.dateOf("createdAt").toString("%m")).as("monthStr"));

        ops.add(group("category", "yearStr", "monthStr")
                .addToSet("orderId").as("orderIds")
                .addToSet("customerRef").as("customerRefs")
                .addToSet("productId").as("productIds")
                .sum("quantity").as("unitsSold")
                .sum("lineRevenue").as("revenue")
                .avg("price").as("avgPrice"));

        ops.add(project("unitsSold", "revenue", "avgPrice")
                .and("_id.category").as("category")
                .and("_id.yearStr").as("yearStr")
                .and("_id.monthStr").as("monthStr")
                .and(ArrayOperators.Size.lengthOfArray("orderIds")).as("orderCount")
                .and(ArrayOperators.Size.lengthOfArray("customerRefs")).as("customerCount")
                .and(ArrayOperators.Size.lengthOfArray("productIds")).as("distinctProducts"));

        ops.add(sort(Sort.by(Sort.Direction.DESC, "yearStr", "monthStr", "revenue")));

        Aggregation agg = newAggregation(ops);
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "orders", Document.class);

        return results.getMappedResults().stream().map(d -> CategoryRevenueDetailResponse.builder()
                .category(d.getString("category"))
                .year(Integer.parseInt(d.getString("yearStr")))
                .month(Integer.parseInt(d.getString("monthStr")))
                .orderCount(asLong(d.get("orderCount")))
                .customerCount(asLong(d.get("customerCount")))
                .unitsSold(asLong(d.get("unitsSold")))
                .revenue(round2(asDouble(d.get("revenue"))))
                .distinctProducts(asLong(d.get("distinctProducts")))
                .averagePrice(round2(asDouble(d.get("avgPrice"))))
                .build()).toList();
    }

    /**
     * Date-range totals without a {@code } to products. The {@code } on
     * {@code createdAt} is the dominant indexed step, so this isolates index impact (S4B).
     */
    public OrderTotalsResponse getOrderTotals(Instant from, Instant to) {
        List<AggregationOperation> ops = new ArrayList<>();
        if (from != null || to != null) {
            Criteria criteria = Criteria.where("createdAt");
            if (from != null) criteria = criteria.gte(Date.from(from));
            if (to != null) criteria = criteria.lte(Date.from(to));
            ops.add(match(criteria));
        }
        ops.add(unwind("items"));
        ops.add(group("_id")
                .sum(ArithmeticOperators.Multiply.valueOf("items.price").multiplyBy("items.quantity")).as("orderRevenue")
                .sum("items.quantity").as("orderUnits"));
        ops.add(group().count().as("orderCount")
                .sum("orderRevenue").as("totalRevenue")
                .sum("orderUnits").as("totalUnits"));

        Aggregation agg = newAggregation(ops);
        Document d = mongoTemplate.aggregate(agg, "orders", Document.class).getUniqueMappedResult();
        if (d == null) {
            return OrderTotalsResponse.builder().orderCount(0L).totalRevenue(0.0).totalUnits(0L).build();
        }
        return OrderTotalsResponse.builder()
                .orderCount(d.get("orderCount") instanceof Number n ? n.longValue() : 0L)
                .totalRevenue(d.get("totalRevenue") instanceof Number n ? Math.round(n.doubleValue() * 100.0) / 100.0 : 0.0)
                .totalUnits(d.get("totalUnits") instanceof Number n ? n.longValue() : 0L)
                .build();
    }
}