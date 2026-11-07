package com.benchmark.relational.store;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ReviewCreateRequest;
import com.benchmark.common.dto.ReviewDto;
import com.benchmark.relational.domain.AttributeDefinition;
import com.benchmark.relational.domain.Category;
import com.benchmark.relational.domain.Product;
import com.benchmark.relational.domain.ProductAttributeValue;
import com.benchmark.relational.domain.Review;
import com.benchmark.relational.mapper.ProductMapper;
import com.benchmark.relational.repository.AttributeDefinitionRepository;
import com.benchmark.relational.repository.CategoryRepository;
import com.benchmark.relational.repository.ProductAttributeValueRepository;
import com.benchmark.relational.repository.ProductRepository;
import com.benchmark.relational.repository.ReviewRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Normalized EAV implementation: products live in {@code product}, dynamic attributes are split
 * into {@code attribute_definition} + {@code product_attribute_value}, and reviews into {@code review}.
 * Dynamic filtering is done with correlated {@code EXISTS} predicates over the attribute tables.
 */
@Component
@ConditionalOnProperty(name = "app.relational.variant", havingValue = "normalized", matchIfMissing = true)
public class NormalizedProductStore implements ProductStore {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "price", "p.price",
            "name", "p.name",
            "sku", "p.sku",
            "stock", "p.stock",
            "createdat", "p.created_at",
            "updatedat", "p.updated_at"
    );

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeDefinitionRepository attributeDefinitionRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ReviewRepository reviewRepository;
    private final ProductMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public NormalizedProductStore(ProductRepository productRepository,
                                  CategoryRepository categoryRepository,
                                  AttributeDefinitionRepository attributeDefinitionRepository,
                                  ProductAttributeValueRepository attributeValueRepository,
                                  ReviewRepository reviewRepository,
                                  ProductMapper mapper,
                                  JdbcTemplate jdbcTemplate) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.attributeDefinitionRepository = attributeDefinitionRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.reviewRepository = reviewRepository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String variant() {
        return "normalized";
    }

    @Override
    @Transactional
    public ProductDto create(ProductCreateRequest request) {
        String id = request.getId() != null && !request.getId().isBlank()
                ? request.getId() : UUID.randomUUID().toString();
        Category category = resolveCategory(request.getCategory());
        Instant now = Instant.now();

        Product product = new Product(id, request.getSku(), request.getName(), category,
                BigDecimal.valueOf(request.getPrice()), request.getStock(), 0, BigDecimal.ZERO, now, now);
        productRepository.save(product);

        applyAttributes(product, category, request.getAttributes());
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDto> findById(String id) {
        return productRepository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional
    public ProductDto update(String id, ProductCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));

        Category category = resolveCategory(request.getCategory());
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setCategory(category);
        product.setPrice(BigDecimal.valueOf(request.getPrice()));
        product.setStock(request.getStock());
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);

        if (request.getAttributes() != null) {
            applyAttributes(product, category, request.getAttributes());
        }
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public boolean deleteById(String id) {
        if (!productRepository.existsById(id)) {
            return false;
        }
        productRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional
    public int bulkInsert(List<ProductCreateRequest> requests) {
        Instant now = Instant.now();
        List<Object[]> productBatch = new ArrayList<>(requests.size());
        List<Object[]> attributeBatch = new ArrayList<>();

        for (ProductCreateRequest request : requests) {
            String id = request.getId() != null && !request.getId().isBlank()
                    ? request.getId() : UUID.randomUUID().toString();
            Category category = resolveCategory(request.getCategory());
            productBatch.add(new Object[]{
                    id, request.getSku(), request.getName(), category.getId(),
                    request.getPrice(), request.getStock(), 0, 0.0,
                    Timestamp.from(now), Timestamp.from(now)
            });
            if (request.getAttributes() != null) {
                for (Map.Entry<String, Object> entry : request.getAttributes().entrySet()) {
                    AttributeDefinition definition = resolveDefinition(category, entry.getKey(), entry.getValue());
                    Object value = entry.getValue();
                    String stringValue = null;
                    BigDecimal numberValue = null;
                    Boolean booleanValue = null;
                    if (value instanceof Number number) {
                        numberValue = new BigDecimal(number.toString());
                    } else if (value instanceof Boolean bool) {
                        booleanValue = bool;
                    } else if (value != null) {
                        stringValue = value.toString();
                    }
                    attributeBatch.add(new Object[]{
                            UUID.randomUUID().toString(), id, definition.getId(), stringValue, numberValue, booleanValue
                    });
                }
            }
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO product (id, sku, name, category_id, price, stock, review_count, rating_avg, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                productBatch);
        if (!attributeBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO product_attribute_value (id, product_id, attribute_definition_id, string_value, number_value, boolean_value) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    attributeBatch);
        }
        return productBatch.size();
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
            where.append(" AND c.name = ?");
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
                where.append(" AND EXISTS (SELECT 1 FROM product_attribute_value pav")
                        .append(" JOIN attribute_definition ad ON pav.attribute_definition_id = ad.id")
                        .append(" WHERE pav.product_id = p.id AND ad.category_id = p.category_id AND ad.name = ?");
                params.add(entry.getKey());
                if (value instanceof Boolean bool) {
                    where.append(" AND pav.boolean_value = ?");
                    params.add(bool);
                } else if (value instanceof Number number) {
                    where.append(" AND pav.number_value = ?");
                    params.add(new BigDecimal(number.toString()));
                } else {
                    where.append(" AND pav.string_value = ?");
                    params.add(String.valueOf(value));
                }
                where.append(")");
            }
        }

        String fromClause = " FROM product p JOIN category c ON p.category_id = c.id" + where;
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
                "SELECT COUNT(*) FROM product p WHERE p.name ILIKE ?", Long.class, like);
        long totalElements = total == null ? 0L : total;

        List<String> ids = jdbcTemplate.queryForList(
                "SELECT p.id FROM product p WHERE p.name ILIKE ? ORDER BY p.name LIMIT ? OFFSET ?",
                String.class, like, size, (long) page * size);

        return buildPage(ids, page, size, totalElements);
    }

    @Override
    @Transactional
    public ProductDto addReview(String id, ReviewCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));

        Review review = new Review(UUID.randomUUID().toString(), product,
                request.getRating(), request.getText(), Instant.now());
        reviewRepository.save(review);

        int newCount = product.getReviewCount() + 1;
        double currentTotal = product.getRatingAvg().doubleValue() * product.getReviewCount();
        double newAvg = Math.round(((currentTotal + request.getRating()) / newCount) * 10.0) / 10.0;
        product.setReviewCount(newCount);
        product.setRatingAvg(BigDecimal.valueOf(newAvg));
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);

        return findById(id).orElseThrow();
    }

    @Override
    public boolean decrementStock(String productId, int quantity) {
        int updated = jdbcTemplate.update(
                "UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?",
                quantity, productId, quantity);
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

    private ProductDto toDto(Product product) {
        String categoryName = product.getCategory().getName();
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (ProductAttributeValue value : attributeValueRepository.findByProductId(product.getId())) {
            attributes.put(value.getAttributeDefinition().getName(),
                    ProductMapper.typedValue(value.getStringValue(), value.getNumberValue(), value.getBooleanValue()));
        }
        List<ReviewDto> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(product.getId()).stream()
                .limit(20)
                .map(mapper::toReviewDto)
                .toList();
        return mapper.toDto(product, categoryName, attributes, reviews);
    }

    private void applyAttributes(Product product, Category category, Map<String, Object> attributes) {
        attributeValueRepository.deleteByProductId(product.getId());
        // Derived deletes are only flushed at commit time, where Hibernate would emit the new
        // INSERTs before the DELETEs and hit the uq_prod_attr unique constraint. Flushing here
        // forces the removals to hit the database first.
        attributeValueRepository.flush();
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            AttributeDefinition definition = resolveDefinition(category, entry.getKey(), entry.getValue());
            Object value = entry.getValue();
            ProductAttributeValue attributeValue = new ProductAttributeValue();
            attributeValue.setId(UUID.randomUUID().toString());
            attributeValue.setProduct(product);
            attributeValue.setAttributeDefinition(definition);
            if (value instanceof Number number) {
                attributeValue.setNumberValue(new BigDecimal(number.toString()));
            } else if (value instanceof Boolean bool) {
                attributeValue.setBooleanValue(bool);
            } else if (value != null) {
                attributeValue.setStringValue(value.toString());
            }
            attributeValueRepository.save(attributeValue);
        }
    }

    private Category resolveCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(new Category(UUID.randomUUID().toString(), name, name)));
    }

    private AttributeDefinition resolveDefinition(Category category, String name, Object value) {
        return attributeDefinitionRepository.findByCategoryIdAndName(category.getId(), name)
                .orElseGet(() -> attributeDefinitionRepository.save(
                        new AttributeDefinition(UUID.randomUUID().toString(), category, name, dataTypeOf(value))));
    }

    private static String dataTypeOf(Object value) {
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return "string";
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
