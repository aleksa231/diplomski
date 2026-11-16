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
     * Join-heavy report (S4): for every category and month it returns revenue, the number of distinct
     * orders, the average basket size and the top 3 products by units sold.
     *
     * <p>Stages: unwind the embedded order items, join a product to resolve the category, split the
     * ISO date into year/month, then a single {@code $facet} branches the stream. One branch groups
     * per category/month (distinct orders, revenue, items); the other groups per category/month/product,
     * sorts by units and keeps only the first three products. Both aggregations stay inside the
     * database, so the response is small regardless of how many order lines the dataset holds.
     */
    public List<CategoryMonthlySummaryResponse> getCategoryMonthlySummary() {
        Document project = new Document("$project", new Document()
                .append("category", "$prod.category")
                .append("orderId", "$_id")
                .append("productId", "$items.productId")
                .append("productName", "$items.productName")
                .append("quantity", "$items.quantity")
                .append("lineRevenue", new Document("$multiply", List.of("$items.price", "$items.quantity")))
                .append("yearStr", new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")))
                .append("monthStr", new Document("$dateToString", new Document("format", "%m").append("date", "$createdAt"))));

        List<Document> summaryPipeline = List.of(
                new Document("$group", new Document("_id", new Document("category", "$category")
                        .append("year", "$yearStr")
                        .append("month", "$monthStr"))
                        .append("revenue", new Document("$sum", "$lineRevenue"))
                        .append("totalItems", new Document("$sum", "$quantity"))
                        .append("orderIds", new Document("$addToSet", "$orderId"))),
                new Document("$project", new Document("_id", 0)
                        .append("category", "$_id.category")
                        .append("year", "$_id.year")
                        .append("month", "$_id.month")
                        .append("revenue", 1)
                        .append("totalItems", 1)
                        .append("orderCount", new Document("$size", "$orderIds"))));

        List<Document> productPipeline = List.of(
                new Document("$group", new Document("_id", new Document("category", "$category")
                        .append("year", "$yearStr")
                        .append("month", "$monthStr")
                        .append("productId", "$productId"))
                        .append("productName", new Document("$first", "$productName"))
                        .append("units", new Document("$sum", "$quantity"))
                        .append("revenue", new Document("$sum", "$lineRevenue"))),
                new Document("$sort", new Document("_id.category", 1)
                        .append("_id.year", -1)
                        .append("_id.month", -1)
                        .append("units", -1)),
                new Document("$group", new Document("_id", new Document("category", "$_id.category")
                        .append("year", "$_id.year")
                        .append("month", "$_id.month"))
                        .append("topProducts", new Document("$push", new Document("productId", "$_id.productId")
                                .append("productName", "$productName")
                                .append("unitsSold", "$units")
                                .append("revenue", "$revenue")))),
                new Document("$project", new Document("topProducts", new Document("$slice", List.of("$topProducts", 3)))));

        Document facet = new Document("$facet", new Document("summary", summaryPipeline)
                .append("byProduct", productPipeline));

        Aggregation agg = newAggregation(
                unwind("items"),
                lookup("products", "items.productId", "_id", "prod"),
                unwind("prod"),
                context -> project,
                context -> facet)
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        Document root = mongoTemplate.aggregate(agg, "orders", Document.class).getUniqueMappedResult();
        if (root == null) {
            return List.of();
        }

        Map<String, List<TopProductResponse>> topProductsByGroup = new HashMap<>();
        for (Document row : root.getList("byProduct", Document.class)) {
            Document id = row.get("_id", Document.class);
            String category = id.getString("category");
            String year = id.getString("year");
            String month = id.getString("month");
            List<TopProductResponse> topProducts = new ArrayList<>();
            for (Document product : row.getList("topProducts", Document.class)) {
                topProducts.add(TopProductResponse.builder()
                        .productId(product.getString("productId"))
                        .productName(product.getString("productName"))
                        .category(category)
                        .unitsSold(asLong(product.get("unitsSold")))
                        .revenue(round2(asDouble(product.get("revenue"))))
                        .build());
            }
            topProductsByGroup.put(groupKey(category, year, month), topProducts);
        }

        List<CategoryMonthlySummaryResponse> summaries = new ArrayList<>();
        for (Document d : root.getList("summary", Document.class)) {
            String category = d.getString("category");
            String year = d.getString("year");
            String month = d.getString("month");
            long orderCount = asLong(d.get("orderCount"));
            long totalItems = asLong(d.get("totalItems"));
            double averageBasketSize = orderCount == 0
                    ? 0.0
                    : Math.round(((double) totalItems / orderCount) * 10.0) / 10.0;

            summaries.add(CategoryMonthlySummaryResponse.builder()
                    .category(category)
                    .year(Integer.parseInt(year))
                    .month(Integer.parseInt(month))
                    .revenue(round2(asDouble(d.get("revenue"))))
                    .orderCount(orderCount)
                    .averageBasketSize(averageBasketSize)
                    .topProducts(topProductsByGroup.getOrDefault(groupKey(category, year, month), List.of()))
                    .build());
        }

        summaries.sort(Comparator.comparing(CategoryMonthlySummaryResponse::getYear).reversed()
                .thenComparing(CategoryMonthlySummaryResponse::getMonth, Comparator.reverseOrder())
                .thenComparing(CategoryMonthlySummaryResponse::getCategory));
        return summaries;
    }

    private static String groupKey(String category, String year, String month) {
        return category + "|" + year + "|" + month;
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