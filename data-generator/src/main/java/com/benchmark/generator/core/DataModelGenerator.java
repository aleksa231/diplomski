package com.benchmark.generator.core;

import com.benchmark.common.dto.OrderDtos;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ReviewDto;

import java.time.Instant;
import java.util.*;

public class DataModelGenerator {

    public static final String[] CATEGORIES = {"laptops", "phones", "clothing", "groceries", "books"};

    public static List<GeneratedProduct> generateProducts(long seed, int count) {
        Random random = new Random(seed);
        List<GeneratedProduct> products = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String id = generateDeterministicUuid(random);
            String category = selectCategory(random);
            String sku = category.substring(0, 3).toUpperCase() + "-" + String.format("%06d", i + 1);
            String name = generateProductName(category, i + 1, random);
            double price = Math.round((5.0 + random.nextDouble() * 1995.0) * 100.0) / 100.0;
            int stock = 10 + random.nextInt(990);

            Map<String, Object> attributes = generateAttributes(category, random);

            int reviewCount = random.nextInt(21); // 0 to 20
            List<ReviewDto> reviews = new ArrayList<>(reviewCount);
            double ratingSum = 0;
            long baseEpoch = 1704067200L + (i * 3600L); // 2024-01-01 onwards
            for (int r = 0; r < reviewCount; r++) {
                int rating = 1 + random.nextInt(5);
                ratingSum += rating;
                reviews.add(ReviewDto.builder()
                        .rating(rating)
                        .text("Deterministic review " + (r + 1) + " for " + sku)
                        .createdAt(Instant.ofEpochSecond(baseEpoch + (r * 1800L)))
                        .build());
            }
            double ratingAvg = reviewCount == 0 ? 0.0 : Math.round((ratingSum / reviewCount) * 10.0) / 10.0;

            Instant createdAt = Instant.ofEpochSecond(baseEpoch);
            Instant updatedAt = createdAt.plusSeconds(3600);

            ProductDto dto = ProductDto.builder()
                    .id(id)
                    .sku(sku)
                    .name(name)
                    .category(category)
                    .price(price)
                    .stock(stock)
                    .attributes(attributes)
                    .reviews(reviews)
                    .reviewCount(reviewCount)
                    .ratingAvg(ratingAvg)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();

            products.add(new GeneratedProduct(dto, attributes));
        }

        return products;
    }

    public static List<OrderDtos.OrderResponse> generateOrders(long seed, int count, List<GeneratedProduct> products) {
        Random random = new Random(seed + 9999);
        List<OrderDtos.OrderResponse> orders = new ArrayList<>(count);

        // Precompute Zipf cumulative distribution
        int productCount = products.size();
        double[] cdf = new double[productCount];
        double sum = 0.0;
        double s = 0.85; // Zipf exponent
        for (int i = 0; i < productCount; i++) {
            sum += 1.0 / Math.pow(i + 1, s);
            cdf[i] = sum;
        }
        for (int i = 0; i < productCount; i++) {
            cdf[i] /= sum;
        }

        for (int i = 0; i < count; i++) {
            String orderId = generateDeterministicUuid(random);
            String customerRef = "cust-" + (1 + random.nextInt(1000));
            int itemCount = 1 + random.nextInt(4);

            List<OrderDtos.OrderItemDto> items = new ArrayList<>(itemCount);
            double total = 0.0;

            for (int j = 0; j < itemCount; j++) {
                int productIndex = sampleZipf(random.nextDouble(), cdf);
                GeneratedProduct p = products.get(productIndex);
                int qty = 1 + random.nextInt(3);
                items.add(OrderDtos.OrderItemDto.builder()
                        .productId(p.dto().getId())
                        .productName(p.dto().getName())
                        .price(p.dto().getPrice())
                        .quantity(qty)
                        .build());
                total += p.dto().getPrice() * qty;
            }

            total = Math.round(total * 100.0) / 100.0;
            Instant createdAt = Instant.ofEpochSecond(1704067200L + (i * 120L));

            List<OrderDtos.OrderStatusHistoryDto> history = List.of(
                    OrderDtos.OrderStatusHistoryDto.builder()
                            .status("CREATED")
                            .timestamp(createdAt)
                            .build()
            );

            orders.add(OrderDtos.OrderResponse.builder()
                    .id(orderId)
                    .customerRef(customerRef)
                    .items(items)
                    .total(total)
                    .status("CREATED")
                    .statusHistory(history)
                    .createdAt(createdAt)
                    .build());
        }

        return orders;
    }

    private static int sampleZipf(double u, double[] cdf) {
        int idx = Arrays.binarySearch(cdf, u);
        if (idx < 0) {
            idx = -idx - 1;
        }
        return Math.min(idx, cdf.length - 1);
    }

    public static String generateDeterministicUuid(Random random) {
        return new UUID(random.nextLong(), random.nextLong()).toString();
    }

    private static String selectCategory(Random random) {
        double r = random.nextDouble();
        if (r < 0.15) return "laptops";
        if (r < 0.40) return "phones";
        if (r < 0.70) return "clothing";
        if (r < 0.90) return "groceries";
        return "books";
    }

    private static String generateProductName(String category, int index, Random random) {
        return switch (category) {
            case "laptops" -> "Laptop Model " + index + " " + (13 + random.nextInt(4)) + " Inch";
            case "phones" -> "Smartphone Nova " + index;
            case "clothing" -> "Premium " + (index % 2 == 0 ? "Cotton Shirt " : "Denim Pants ") + index;
            case "groceries" -> "Organic Item " + index;
            case "books" -> "Volume " + index + ": The Great Chronicle";
            default -> "Product " + index;
        };
    }

    private static Map<String, Object> generateAttributes(String category, Random random) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        switch (category) {
            case "laptops" -> {
                attrs.put("cpu", random.nextBoolean() ? "Intel Core i7" : "AMD Ryzen 7");
                attrs.put("ramGb", List.of(8, 16, 32, 64).get(random.nextInt(4)));
                attrs.put("storageGb", List.of(256, 512, 1024, 2048).get(random.nextInt(4)));
                attrs.put("screenInch", List.of(13.3, 14.0, 15.6, 16.0).get(random.nextInt(4)));
                attrs.put("gpu", random.nextBoolean() ? "integrated" : "RTX 4060");
                attrs.put("weightKg", Math.round((1.2 + random.nextDouble()) * 10.0) / 10.0);
            }
            case "phones" -> {
                attrs.put("os", random.nextBoolean() ? "Android" : "iOS");
                attrs.put("ramGb", List.of(4, 6, 8, 12).get(random.nextInt(4)));
                attrs.put("storageGb", List.of(64, 128, 256, 512).get(random.nextInt(4)));
                attrs.put("batteryMah", List.of(3500, 4000, 4500, 5000).get(random.nextInt(4)));
                attrs.put("cameraMp", List.of(12, 48, 50, 108).get(random.nextInt(4)));
                attrs.put("fiveG", random.nextBoolean());
            }
            case "clothing" -> {
                attrs.put("size", List.of("S", "M", "L", "XL").get(random.nextInt(4)));
                attrs.put("color", List.of("black", "white", "blue", "red").get(random.nextInt(4)));
                attrs.put("material", List.of("cotton", "wool", "polyester").get(random.nextInt(3)));
                attrs.put("gender", List.of("men", "women", "unisex").get(random.nextInt(3)));
                attrs.put("season", List.of("spring", "summer", "autumn", "winter").get(random.nextInt(4)));
            }
            case "groceries" -> {
                attrs.put("weightG", List.of(250, 500, 1000).get(random.nextInt(3)));
                attrs.put("organic", random.nextBoolean());
                attrs.put("expiryDays", List.of(7, 14, 30, 90).get(random.nextInt(4)));
                attrs.put("origin", List.of("Italy", "Spain", "France").get(random.nextInt(3)));
                attrs.put("allergens", random.nextBoolean() ? List.of("gluten") : List.of());
            }
            case "books" -> {
                attrs.put("author", List.of("George Orwell", "Isaac Asimov", "Agatha Christie").get(random.nextInt(3)));
                attrs.put("pages", 150 + random.nextInt(600));
                attrs.put("language", List.of("English", "Spanish", "French").get(random.nextInt(3)));
                attrs.put("isbn", "978-0-" + (100000 + random.nextInt(900000)));
                attrs.put("genre", List.of("Sci-Fi", "Fantasy", "Mystery").get(random.nextInt(3)));
            }
        }
        return attrs;
    }

    public record GeneratedProduct(ProductDto dto, Map<String, Object> rawAttributes) {}
}
