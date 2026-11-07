package com.benchmark.relational.store;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ReviewCreateRequest;
import com.benchmark.common.dto.ReviewDto;
import com.benchmark.relational.domain.Category;
import com.benchmark.relational.domain.Product;
import com.benchmark.relational.domain.ProductJsonb;
import com.benchmark.relational.domain.Review;
import com.benchmark.relational.mapper.ProductMapper;
import com.benchmark.relational.repository.CategoryRepository;
import com.benchmark.relational.repository.ProductJsonbRepository;
import com.benchmark.relational.repository.ProductRepository;
import com.benchmark.relational.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * JSONB implementation: products and their dynamic attributes live in {@code product_jsonb}
 * ({@code attributes} column with a GIN index), and dynamic filtering uses typed JSONB
 * containment ({@code attributes @> ?}) so a numeric filter only matches numeric JSON values.
 *
 * <p>A minimal row is also maintained in {@code product} (and {@code category}) for the same id,
 * because {@code review}, {@code cart_item} and {@code order_item} keep foreign keys to
 * {@code product(id)}. Those tables always use the relational model regardless of the variant.
 */
@Component
@ConditionalOnProperty(name = "app.relational.variant", havingValue = "jsonb")
public class JsonbProductStore implements ProductStore {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "price", "p.price",
            "name", "p.name",
            "sku", "p.sku",
            "stock", "p.stock",
            "createdat", "p.created_at",
            "updatedat", "p.updated_at"
    );

    private final ProductJsonbRepository jsonbRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final ProductMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonbProductStore(ProductJsonbRepository jsonbRepository,
                             ProductRepository productRepository,
                             CategoryRepository categoryRepository,
                             ReviewRepository reviewRepository,
                             ProductMapper mapper,
                             JdbcTemplate jdbcTemplate) {
        this.jsonbRepository = jsonbRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.reviewRepository = reviewRepository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String variant() {
        return "jsonb";
    }

    @Override
    @Transactional
    public ProductDto create(ProductCreateRequest request) {
        String id = request.getId() != null && !request.getId().isBlank()
                ? request.getId() : UUID.randomUUID().toString();
        Instant now = Instant.now();

        ProductJsonb product = new ProductJsonb();
        product.setId(id);
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setPrice(BigDecimal.valueOf(request.getPrice()));
        product.setStock(request.getStock());
        product.setAttributes(request.getAttributes() != null ? request.getAttributes() : new HashMap<>());
        product.setReviewCount(0);
        product.setRatingAvg(BigDecimal.ZERO);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        jsonbRepository.save(product);

        ensureShadowProduct(product);
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDto> findById(String id) {
        return jsonbRepository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional
    public ProductDto update(String id, ProductCreateRequest request) {
        ProductJsonb product = jsonbRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setPrice(BigDecimal.valueOf(request.getPrice()));
        product.setStock(request.getStock());
        if (request.getAttributes() != null) {
            product.setAttributes(request.getAttributes());
        }
        product.setUpdatedAt(Instant.now());
        jsonbRepository.save(product);

        productRepository.findById(id).ifPresent(shadow -> {
            shadow.setSku(request.getSku());
            shadow.setName(request.getName());
            shadow.setCategory(resolveCategory(request.getCategory()));
            shadow.setPrice(BigDecimal.valueOf(request.getPrice()));
            shadow.setStock(request.getStock());
            shadow.setUpdatedAt(product.getUpdatedAt());
            productRepository.save(shadow);
        });
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public boolean deleteById(String id) {
        if (!jsonbRepository.existsById(id)) {
            return false;
        }
        jsonbRepository.deleteById(id);
        productRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional
    public int bulkInsert(List<ProductCreateRequest> requests) {
        Instant now = Instant.now();
        List<Object[]> jsonbBatch = new ArrayList<>(requests.size());
        List<Object[]> shadowBatch = new ArrayList<>(requests.size());

        for (ProductCreateRequest request : requests) {
            String id = request.getId() != null && !request.getId().isBlank()
                    ? request.getId() : UUID.randomUUID().toString();
            Map<String, Object> attributes = request.getAttributes() != null ? request.getAttributes() : new HashMap<>();
            Category category = resolveCategory(request.getCategory());

            jsonbBatch.add(new Object[]{
                    id, request.getSku(), request.getName(), request.getCategory(),
                    request.getPrice(), request.getStock(), jsonbObject(attributes), 0, 0.0,
                    Timestamp.from(now), Timestamp.from(now)
            });
            shadowBatch.add(new Object[]{
                    id, request.getSku(), request.getName(), category.getId(),
                    request.getPrice(), request.getStock(), 0, 0.0,
                    Timestamp.from(now), Timestamp.from(now)
            });
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO product_jsonb (id, sku, name, category, price, stock, attributes, review_count, rating_avg, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                jsonbBatch);
        jdbcTemplate.batchUpdate(
                "INSERT INTO product (id, sku, name, category_id, price, stock, review_count, rating_avg, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                shadowBatch);
        return jsonbBatch.size();
    }

    @Override
    @Transactional(readOnly = true)
    public PageProductResponse query(String category,
                                     Double minPrice,
                                     Double maxPrice,
                                     Map<String, String> dynamicAttrs,
                                     String sortParam,
                                     int page,
                                     int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            where.append(" AND p.category = ?");
            params.add(category);
        }
        if (minPrice != null) {
            where.append(" AND p.price >= ?");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND p.price <= ?");
            params.add(maxPrice);
        }
        if (dynamicAttrs != null) {
            for (Map.Entry<String, String> entry : dynamicAttrs.entrySet()) {
                Object value = AttributeValueParser.parse(entry.getValue());
                where.append(" AND p.attributes @> ?");
                params.add(jsonbObject(Map.of(entry.getKey(), value)));
            }
        }

        String fromClause = " FROM product_jsonb p" + where;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + fromClause, Long.class, params.toArray());
        long totalElements = total == null ? 0L : total;

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT p.id" + fromClause + buildOrderBy(sortParam) + " LIMIT ? OFFSET ?",
                String.class, pageParams.toArray());

        return buildPage(ids, page, size, totalElements);
    }

    @Override
    @Transactional(readOnly = true)
    public PageProductResponse search(String q, int page, int size) {
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_jsonb p WHERE p.name ILIKE ?", Long.class, like);
        long totalElements = total == null ? 0L : total;

        List<String> ids = jdbcTemplate.queryForList(
                "SELECT p.id FROM product_jsonb p WHERE p.name ILIKE ? ORDER BY p.name LIMIT ? OFFSET ?",
                String.class, like, size, (long) page * size);

        return buildPage(ids, page, size, totalElements);
    }

    @Override
    @Transactional
    public ProductDto addReview(String id, ReviewCreateRequest request) {
        ProductJsonb product = jsonbRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));

        ensureShadowProduct(product);
        Product shadow = productRepository.findById(id).orElseThrow();
        Review review = new Review(UUID.randomUUID().toString(), shadow,
                request.getRating(), request.getText(), Instant.now());
        reviewRepository.save(review);

        int newCount = product.getReviewCount() + 1;
        double currentTotal = product.getRatingAvg().doubleValue() * product.getReviewCount();
        double newAvg = Math.round(((currentTotal + request.getRating()) / newCount) * 10.0) / 10.0;
        product.setReviewCount(newCount);
        product.setRatingAvg(BigDecimal.valueOf(newAvg));
        product.setUpdatedAt(Instant.now());
        jsonbRepository.save(product);

        return findById(id).orElseThrow();
    }

    @Override
    public boolean decrementStock(String productId, int quantity) {
        int updated = jdbcTemplate.update(
                "UPDATE product_jsonb SET stock = stock - ? WHERE id = ? AND stock >= ?",
                quantity, productId, quantity);
        if (updated == 1) {
            // Keep the FK-anchor row's stock consistent; it is never read in this variant.
            jdbcTemplate.update("UPDATE product SET stock = GREATEST(stock - ?, 0) WHERE id = ?",
                    quantity, productId);
        }
        return updated == 1;
    }

    private PageProductResponse buildPage(List<String> ids, int page, int size, long totalElements) {
        List<ProductDto> content = ids.stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        return PageProductResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    private ProductDto toDto(ProductJsonb product) {
        List<ReviewDto> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(product.getId()).stream()
                .limit(20)
                .map(mapper::toReviewDto)
                .toList();
        return mapper.toDto(product, reviews);
    }

    private void ensureShadowProduct(ProductJsonb product) {
        if (productRepository.existsById(product.getId())) {
            return;
        }
        Category category = resolveCategory(product.getCategory());
        productRepository.save(new Product(product.getId(), product.getSku(), product.getName(), category,
                product.getPrice(), product.getStock(), product.getReviewCount(), product.getRatingAvg(),
                product.getCreatedAt(), product.getUpdatedAt()));
    }

    private Category resolveCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(new Category(UUID.randomUUID().toString(), name, name)));
    }

    private PGobject jsonbObject(Object value) {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(objectMapper.writeValueAsString(value));
            return object;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize attribute to JSONB", ex);
        }
    }

    private String buildOrderBy(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return " ORDER BY p.created_at DESC";
        }
        String[] parts = sortParam.split(",");
        String column = SORT_COLUMNS.get(parts[0].trim().toLowerCase());
        if (column == null) {
            return " ORDER BY p.created_at DESC";
        }
        String direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? "DESC" : "ASC";
        return " ORDER BY " + column + " " + direction;
    }
}
