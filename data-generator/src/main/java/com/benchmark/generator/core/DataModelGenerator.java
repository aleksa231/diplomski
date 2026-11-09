package com.benchmark.generator.core;

import com.benchmark.common.dto.OrderDtos;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ReviewDto;

import java.time.Instant;
import java.util.*;

public class DataModelGenerator {

    public static final String[] CATEGORIES = {"laptops", "phones", "clothing", "groceries", "books"};

    /**
     * Groceries get realistic Serbian names and prices instead of a single "Organic Item N"
     * template. The weight is part of the generated document too, so the name and the attribute
     * agree.
     */
    private record GroceryItem(String name, String pack, int weightG, double minPrice, double maxPrice) {}

    private static final GroceryItem[] GROCERY_ITEMS = {
            new GroceryItem("Jabuke", "1 kg", 1000, 1.49, 2.99),
            new GroceryItem("Banane", "1 kg", 1000, 1.29, 2.49),
            new GroceryItem("Pomorandže", "2 kg", 2000, 2.99, 4.99),
            new GroceryItem("Jagode", "500 g", 500, 2.99, 5.99),
            new GroceryItem("Paradajz", "1 kg", 1000, 1.49, 3.49),
            new GroceryItem("Krompir", "2 kg", 2000, 1.99, 3.99),
            new GroceryItem("Crni luk", "1 kg", 1000, 1.29, 2.49),
            new GroceryItem("Šargarepa", "1 kg", 1000, 1.19, 2.29),
            new GroceryItem("Krastavci", "500 g", 500, 0.99, 1.99),
            new GroceryItem("Zelena salata", "1 glavica", 300, 0.99, 1.79),
            new GroceryItem("Mlijeko", "1 L", 1000, 1.19, 2.29),
            new GroceryItem("Grčki jogurt", "500 g", 500, 1.99, 3.49),
            new GroceryItem("Gauda sir", "200 g", 200, 2.49, 4.49),
            new GroceryItem("Maslac", "250 g", 250, 2.79, 4.29),
            new GroceryItem("Jaja iz slobodnog uzgoja", "10 kom", 600, 2.49, 4.49),
            new GroceryItem("Pileća prsa", "500 g", 500, 3.99, 6.49),
            new GroceryItem("Mljevena junetina", "500 g", 500, 4.49, 7.49),
            new GroceryItem("File lososa", "300 g", 300, 6.99, 11.99),
            new GroceryItem("Špagete", "500 g", 500, 1.29, 2.49),
            new GroceryItem("Basmati riža", "1 kg", 1000, 1.79, 3.49),
            new GroceryItem("Pšenično brašno", "1 kg", 1000, 0.99, 1.99),
            new GroceryItem("Šećer", "1 kg", 1000, 1.09, 1.99),
            new GroceryItem("Maslinovo ulje", "750 ml", 750, 7.99, 14.99),
            new GroceryItem("Suncokretovo ulje", "1 L", 1000, 2.49, 4.49),
            new GroceryItem("Mljevena kafa", "250 g", 250, 4.99, 9.99),
            new GroceryItem("Crni čaj", "20 kesica", 100, 2.49, 4.99),
            new GroceryItem("Sok od pomorandže", "1 L", 1000, 1.79, 3.29),
            new GroceryItem("Mineralna voda", "1,5 L", 1500, 0.59, 1.29),
            new GroceryItem("Integralni hljeb", "500 g", 500, 1.49, 2.99),
            new GroceryItem("Crna čokolada", "100 g", 100, 1.49, 2.99),
    };

    private static final String[] GROCERY_BRANDS = {
            "Zlatna žetva", "Priroda plus", "Domaća korpa", "Zeleno polje", "Sunčano polje", "Svježa berba"
    };

    /**
     * Clothing is named after real garment types instead of one repeated prefix, so the
     * catalog reads like a shop. Material, gender and season come from the same entry so
     * the name and the attributes always agree.
     */
    private record ClothingItem(String name, String material, String gender, String season,
                                double minPrice, double maxPrice) {}

    private static final ClothingItem[] CLOTHING_ITEMS = {
            new ClothingItem("Pamučna majica", "pamuk", "uniseks", "ljeto", 8.99, 19.99),
            new ClothingItem("Oversize duks", "pamuk", "uniseks", "jesen", 24.99, 49.99),
            new ClothingItem("Duks s kapuljačom", "pamuk", "uniseks", "zima", 29.99, 59.99),
            new ClothingItem("Kožna jakna", "koža", "ženski", "jesen", 79.99, 249.99),
            new ClothingItem("Traper jakna", "traper", "uniseks", "proljeće", 39.99, 89.99),
            new ClothingItem("Bomber jakna", "poliester", "muški", "jesen", 34.99, 79.99),
            new ClothingItem("Zimska parka", "poliester", "ženski", "zima", 79.99, 199.99),
            new ClothingItem("Vuneni kaput", "vuna", "ženski", "zima", 69.99, 179.99),
            new ClothingItem("Sako od tviida", "vuna", "muški", "jesen", 59.99, 149.99),
            new ClothingItem("Pleteni kardigan", "vuna", "ženski", "jesen", 29.99, 59.99),
            new ClothingItem("Vuneni džemper", "vuna", "muški", "zima", 24.99, 54.99),
            new ClothingItem("Lanena košulja", "lan", "muški", "ljeto", 24.99, 49.99),
            new ClothingItem("Karirana košulja", "pamuk", "muški", "jesen", 19.99, 39.99),
            new ClothingItem("Bijela košulja", "pamuk", "muški", "sve sezone", 19.99, 44.99),
            new ClothingItem("Polo majica", "pamuk", "muški", "ljeto", 14.99, 34.99),
            new ClothingItem("Svilena bluza", "svila", "ženski", "sve sezone", 34.99, 79.99),
            new ClothingItem("Tunika", "lan", "ženski", "ljeto", 19.99, 44.99),
            new ClothingItem("Midi haljina", "viskoza", "ženski", "proljeće", 29.99, 69.99),
            new ClothingItem("Ljetna haljina", "pamuk", "ženski", "ljeto", 24.99, 59.99),
            new ClothingItem("Svečana haljina", "svila", "ženski", "sve sezone", 49.99, 129.99),
            new ClothingItem("Plisirana suknja", "poliester", "ženski", "proljeće", 19.99, 49.99),
            new ClothingItem("Farmerke slim kroja", "traper", "uniseks", "sve sezone", 29.99, 69.99),
            new ClothingItem("Široke pantalone", "lan", "ženski", "ljeto", 24.99, 54.99),
            new ClothingItem("Cargo pantalone", "pamuk", "muški", "proljeće", 24.99, 49.99),
            new ClothingItem("Trenerka", "poliester", "uniseks", "sve sezone", 34.99, 79.99),
            new ClothingItem("Šorc za trčanje", "poliester", "muški", "ljeto", 14.99, 34.99),
            new ClothingItem("Šorts za plažu", "pamuk", "muški", "ljeto", 12.99, 29.99),
            new ClothingItem("Zimske hlače", "vuna", "ženski", "zima", 29.99, 59.99),
            new ClothingItem("Prsluk bez rukava", "poliester", "uniseks", "jesen", 19.99, 44.99),
            new ClothingItem("Kišna jakna", "poliester", "uniseks", "proljeće", 39.99, 99.99),
    };

    private static final String[] CLOTHING_BRANDS = {
            "Aurora", "Nordline", "Vila Moda", "Urbanist", "Terra", "Studio 9"
    };

    /** Laptops cycle through brand/line/screen combinations instead of one "Laptop Model N" template. */
    private static final String[] LAPTOP_BRANDS = {
            "Aurora", "Nordline", "Terra", "Vertex", "Lumen", "Studio 9"
    };

    private static final String[] LAPTOP_LINES = {
            "Air", "Pro", "Studio", "Book", "Ultra", "Flip"
    };

    private static final double[] LAPTOP_SCREENS = {13.3, 14.0, 15.6, 16.0};

    /** Phones get a distinct brand/model with specs that match that model. */
    private record PhoneItem(String brand, String model, int ramGb, int storageGb, int batteryMah,
                             int cameraMp, boolean fiveG, double minPrice, double maxPrice) {}

    private static final PhoneItem[] PHONE_ITEMS = {
            new PhoneItem("Nova", "Lite", 4, 64, 4000, 12, false, 99, 179),
            new PhoneItem("Nova", "Edge", 8, 256, 5000, 50, true, 399, 549),
            new PhoneItem("Aurora", "Pulse", 6, 128, 4500, 48, true, 249, 349),
            new PhoneItem("Aurora", "Max", 8, 256, 5000, 108, true, 599, 799),
            new PhoneItem("Vertex", "One", 4, 128, 4000, 12, false, 149, 229),
            new PhoneItem("Vertex", "Prime", 8, 256, 4800, 64, true, 449, 599),
            new PhoneItem("Lumen", "Mini", 4, 64, 3500, 12, false, 129, 189),
            new PhoneItem("Lumen", "Zoom", 12, 512, 5000, 108, true, 899, 1299),
            new PhoneItem("Terra", "Go", 4, 128, 4000, 48, false, 179, 249),
            new PhoneItem("Terra", "Plus", 6, 256, 4500, 50, true, 329, 449),
            new PhoneItem("Studio 9", "Neo", 8, 128, 4200, 48, false, 279, 399),
            new PhoneItem("Studio 9", "Ultra", 12, 512, 5000, 108, true, 999, 1399),
    };

    /** Books get real titles/authors so the catalog no longer repeats "Tom N: Velika hronika". */
    private record BookItem(String title, String author, String genre, double minPrice, double maxPrice) {}

    private static final BookItem[] BOOK_ITEMS = {
            new BookItem("Šapat starog grada", "Jelena Marić", "misterija", 8.99, 16.99),
            new BookItem("Posljednji voz za jug", "Nikola Pavlović", "drama", 7.99, 14.99),
            new BookItem("Zvijezde nad Vardarom", "Ana Ilić", "naučna fantastika", 9.99, 18.99),
            new BookItem("Kuća od stakla", "Marko Jovanović", "triler", 8.99, 17.99),
            new BookItem("Ljeto kad je padala kiša", "Ivana Petrović", "ljubavni", 6.99, 13.99),
            new BookItem("Tunel ispod grada", "Stefan Đurić", "fantazija", 9.99, 19.99),
            new BookItem("Prašina i med", "Milica Savić", "istorijski", 10.99, 21.99),
            new BookItem("Glas iz tišine", "Luka Stanković", "misterija", 8.99, 16.99),
            new BookItem("Put bez povratka", "Jovana Kovačević", "triler", 9.99, 18.99),
            new BookItem("Vrt na kraju svijeta", "Petar Milić", "fantazija", 10.99, 22.99),
            new BookItem("Soba bez prozora", "Dragana Simić", "psihološki", 8.99, 15.99),
            new BookItem("Ostrvo zaboravljenih", "Nemanja Radović", "avantura", 9.99, 19.99),
            new BookItem("Krv i masline", "Katarina Nikolić", "istorijski", 11.99, 23.99),
            new BookItem("Zima u malom gradu", "Miloš Babić", "drama", 7.99, 14.99),
            new BookItem("Tajna plavog orla", "Sara Lazić", "avantura", 7.99, 15.99),
            new BookItem("Sjene na zidu", "Vuk Đorđević", "horor", 9.99, 17.99),
            new BookItem("Pjesma o izgubljenom ljetu", "Teodora Živković", "ljubavni", 6.99, 14.99),
            new BookItem("Hronika srebrnog jezera", "Andrija Todorović", "fantazija", 10.99, 21.99),
            new BookItem("Dan kada je stao sat", "Marina Kostić", "naučna fantastika", 8.99, 17.99),
            new BookItem("Ispod površine", "Ognjen Ilić", "triler", 7.99, 15.99),
    };

    public static List<GeneratedProduct> generateProducts(long seed, int count) {
        Random random = new Random(seed);
        List<GeneratedProduct> products = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String id = generateDeterministicUuid(random);
            String category = selectCategory(random);
            String sku = category.substring(0, 3).toUpperCase() + "-" + String.format("%06d", i + 1);
            String name = generateProductName(category, i + 1, random);
            double price = generatePrice(category, i + 1, random);
            int stock = 10 + random.nextInt(990);

            Map<String, Object> attributes = generateAttributes(category, i + 1, random);

            int reviewCount = random.nextInt(21); // 0 to 20
            List<ReviewDto> reviews = new ArrayList<>(reviewCount);
            double ratingSum = 0;
            long baseEpoch = 1704067200L + (i * 3600L); // 2024-01-01 onwards
            for (int r = 0; r < reviewCount; r++) {
                int rating = 1 + random.nextInt(5);
                ratingSum += rating;
                reviews.add(ReviewDto.builder()
                        .rating(rating)
                        .text("Recenzija " + (r + 1) + " za " + sku)
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
            Instant createdAt = Instant.ofEpochSecond(1790812800L + (i * 120L)); // 2026-10-01 onwards

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
            case "laptops" -> "Laptop " + laptopBrand(index) + " " + laptopLine(index) + " " + index
                    + ", " + formatInches(laptopScreen(index)) + " inča";
            case "phones" -> {
                PhoneItem item = phoneItem(index);
                yield item.brand() + " " + item.model() + " " + index;
            }
            case "clothing" -> {
                ClothingItem item = clothingItem(index);
                String brand = CLOTHING_BRANDS[random.nextInt(CLOTHING_BRANDS.length)];
                yield brand + " " + item.name().toLowerCase(Locale.ROOT) + " " + index;
            }
            case "groceries" -> {
                GroceryItem item = groceryItem(index);
                String brand = GROCERY_BRANDS[random.nextInt(GROCERY_BRANDS.length)];
                yield brand + " " + item.name().toLowerCase(Locale.ROOT) + " " + item.pack();
            }
            case "books" -> {
                BookItem item = bookItem(index);
                yield item.title() + " " + index;
            }
            default -> "Proizvod " + index;
        };
    }

    private static double generatePrice(String category, int index, Random random) {
        return switch (category) {
            case "groceries" -> {
                GroceryItem item = groceryItem(index);
                yield priceBetween(item.minPrice(), item.maxPrice(), random);
            }
            case "clothing" -> {
                ClothingItem item = clothingItem(index);
                yield priceBetween(item.minPrice(), item.maxPrice(), random);
            }
            case "books" -> {
                BookItem item = bookItem(index);
                yield priceBetween(item.minPrice(), item.maxPrice(), random);
            }
            case "phones" -> {
                PhoneItem item = phoneItem(index);
                yield priceBetween(item.minPrice(), item.maxPrice(), random);
            }
            case "laptops" -> {
                double[] range = laptopPriceRange(laptopLine(index));
                yield priceBetween(range[0], range[1], random);
            }
            default -> priceBetween(5.0, 1995.0, random);
        };
    }

    private static double priceBetween(double min, double max, Random random) {
        return Math.round((min + random.nextDouble() * (max - min)) * 100.0) / 100.0;
    }

    private static double[] laptopPriceRange(String line) {
        return switch (line) {
            case "Air" -> new double[]{549, 999};
            case "Pro" -> new double[]{999, 1999};
            case "Book" -> new double[]{399, 799};
            case "Studio" -> new double[]{699, 1199};
            case "Ultra" -> new double[]{1499, 2499};
            case "Flip" -> new double[]{899, 1399};
            default -> new double[]{499, 1499};
        };
    }

    private static String laptopBrand(int index) {
        return LAPTOP_BRANDS[Math.floorMod(index - 1, LAPTOP_BRANDS.length)];
    }

    private static String laptopLine(int index) {
        return LAPTOP_LINES[Math.floorMod(index - 1, LAPTOP_LINES.length)];
    }

    private static double laptopScreen(int index) {
        return LAPTOP_SCREENS[Math.floorMod(index - 1, LAPTOP_SCREENS.length)];
    }

    private static String formatInches(double inches) {
        if (inches == Math.rint(inches)) {
            return String.valueOf((int) inches);
        }
        return String.valueOf(inches).replace('.', ',');
    }

    private static GroceryItem groceryItem(int index) {
        return GROCERY_ITEMS[Math.floorMod(index - 1, GROCERY_ITEMS.length)];
    }

    private static ClothingItem clothingItem(int index) {
        return CLOTHING_ITEMS[Math.floorMod(index - 1, CLOTHING_ITEMS.length)];
    }

    private static PhoneItem phoneItem(int index) {
        return PHONE_ITEMS[Math.floorMod(index - 1, PHONE_ITEMS.length)];
    }

    private static BookItem bookItem(int index) {
        return BOOK_ITEMS[Math.floorMod(index - 1, BOOK_ITEMS.length)];
    }

    private static Map<String, Object> generateAttributes(String category, int index, Random random) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        switch (category) {
            case "laptops" -> {
                attrs.put("cpu", random.nextBoolean() ? "Intel Core i7" : "AMD Ryzen 7");
                attrs.put("ramGb", List.of(8, 16, 32, 64).get(random.nextInt(4)));
                attrs.put("storageGb", List.of(256, 512, 1024, 2048).get(random.nextInt(4)));
                attrs.put("screenInch", laptopScreen(index));
                attrs.put("gpu", random.nextBoolean() ? "integrisana" : "RTX 4060");
                attrs.put("weightKg", Math.round((1.2 + random.nextDouble()) * 10.0) / 10.0);
            }
            case "phones" -> {
                PhoneItem item = phoneItem(index);
                attrs.put("os", random.nextBoolean() ? "Android" : "iOS");
                attrs.put("ramGb", item.ramGb());
                attrs.put("storageGb", item.storageGb());
                attrs.put("batteryMah", item.batteryMah());
                attrs.put("cameraMp", item.cameraMp());
                attrs.put("fiveG", item.fiveG());
            }
            case "clothing" -> {
                ClothingItem item = clothingItem(index);
                attrs.put("size", List.of("S", "M", "L", "XL").get(random.nextInt(4)));
                attrs.put("color", List.of("crna", "bijela", "plava", "crvena").get(random.nextInt(4)));
                attrs.put("material", item.material());
                attrs.put("gender", item.gender());
                attrs.put("season", item.season());
            }
            case "groceries" -> {
                GroceryItem item = groceryItem(index);
                attrs.put("weightG", item.weightG());
                attrs.put("organic", random.nextBoolean());
                attrs.put("expiryDays", List.of(7, 14, 30, 90).get(random.nextInt(4)));
                attrs.put("origin", List.of("Italija", "Španija", "Francuska").get(random.nextInt(3)));
                attrs.put("allergens", random.nextBoolean() ? List.of("gluten") : List.of());
            }
            case "books" -> {
                BookItem item = bookItem(index);
                attrs.put("author", item.author());
                attrs.put("pages", 150 + random.nextInt(600));
                attrs.put("language", "srpski");
                attrs.put("isbn", "978-0-" + (100000 + random.nextInt(900000)));
                attrs.put("genre", item.genre());
            }
        }
        return attrs;
    }

    public record GeneratedProduct(ProductDto dto, Map<String, Object> rawAttributes) {}
}
