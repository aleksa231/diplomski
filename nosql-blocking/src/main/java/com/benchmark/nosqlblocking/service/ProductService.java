package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ProductWithRecommendationsResponse;
import com.benchmark.common.dto.ReviewCreateRequest;
import com.benchmark.common.dto.ReviewDto;
import com.benchmark.nosqlblocking.cache.CacheConfig;
import com.benchmark.nosqlblocking.cache.QueryKeyFactory;
import com.benchmark.nosqlblocking.domain.ProductDocument;
import com.benchmark.nosqlblocking.mapper.ProductMapper;
import com.benchmark.nosqlblocking.metrics.BenchmarkMetrics;
import com.benchmark.nosqlblocking.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;
    private final CatalogQueryService catalogQueryService;
    private final PopularityService popularityService;
    private final IndexAndExplainService indexAndExplainService;
    private final ProductMapper mapper;
    private final CacheManager cacheManager;
    private final RestClient stubRestClient;
    private final BenchmarkMetrics metrics;

    public ProductService(ProductRepository productRepository,
                          MongoTemplate mongoTemplate,
                          CatalogQueryService catalogQueryService,
                          PopularityService popularityService,
                          IndexAndExplainService indexAndExplainService,
                          ProductMapper mapper,
                          CacheManager cacheManager,
                          RestClient stubRestClient,
                          BenchmarkMetrics metrics) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
        this.catalogQueryService = catalogQueryService;
        this.popularityService = popularityService;
        this.indexAndExplainService = indexAndExplainService;
        this.mapper = mapper;
        this.cacheManager = cacheManager;
        this.stubRestClient = stubRestClient;
        this.metrics = metrics;
    }

    /**
     * Catalog list query. The result is served from the Redis catalog cache when enabled;
     * the key is the normalized query hash.
     */
    public PageProductResponse findProducts(String category,
                                            Double minPrice,
                                            Double maxPrice,
                                            Map<String, String> dynamicAttrs,
                                            String sortParam,
                                            int page,
                                            int size) {
        String cacheKey = QueryKeyFactory.catalogKey(category, minPrice, maxPrice, dynamicAttrs, sortParam, page, size);
        return catalogQueryService.loadPage(cacheKey, category, minPrice, maxPrice, dynamicAttrs, sortParam, page, size);
    }

    public ProductDto getProductById(String id) {
        ProductDocument doc = metrics.time("mongo", "products.findById", () -> productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id)));
        popularityService.recordView(id);
        return mapper.toDto(doc);
    }

    public ProductDto createProduct(ProductCreateRequest req) {
        String id = req.getId() != null && !req.getId().isBlank() ? req.getId() : UUID.randomUUID().toString();
        ProductDocument doc = buildDocument(id, req);
        ProductDocument saved = productRepository.save(doc);
        evictCatalogCache();
        return mapper.toDto(saved);
    }

    public ProductDto updateProduct(String id, ProductCreateRequest req) {
        ProductDocument doc = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));

        doc.setSku(req.getSku());
        doc.setName(req.getName());
        doc.setCategory(req.getCategory());
        doc.setPrice(req.getPrice());
        doc.setStock(req.getStock());
        if (req.getAttributes() != null) {
            doc.setAttributes(req.getAttributes());
        }
        doc.setUpdatedAt(Instant.now());

        ProductDocument saved = productRepository.save(doc);
        evictCatalogCache();
        return mapper.toDto(saved);
    }

    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new NoSuchElementException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
        evictCatalogCache();
    }

    public int bulkInsert(List<ProductCreateRequest> requests) {
        List<ProductDocument> docs = new ArrayList<>(requests.size());
        for (ProductCreateRequest req : requests) {
            String id = req.getId() != null && !req.getId().isBlank() ? req.getId() : UUID.randomUUID().toString();
            docs.add(buildDocument(id, req));
        }
        var inserted = metrics.time("mongo", "products.bulkInsert", () -> mongoTemplate.insertAll(docs));
        evictCatalogCache();
        return inserted.size();
    }

    /**
     * Full-text search. Uses the {@code $text} operator when the experiment text index exists (S4 with
     * indexes enabled). MongoDB cannot run {@code $text} without that index, so with the indexes dropped
     * it degrades to a quoted regular-expression scan — the same "no index" baseline PostgreSQL gets.
     */
    public PageProductResponse searchProducts(String q, int page, int size) {
        Query query = new Query();
        if (q != null && !q.isBlank()) {
            if (indexAndExplainService.isIndexesEnabled()) {
                query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(q.trim().split("\\s+")));
            } else {
                query.addCriteria(Criteria.where("name").regex(Pattern.quote(q), "i"));
            }
        }
        long total = mongoTemplate.count(query, ProductDocument.class);
        query.skip((long) page * size).limit(size);

        List<ProductDocument> docs = mongoTemplate.find(query, ProductDocument.class);
        List<ProductDto> content = docs.stream().map(mapper::toDto).toList();
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) total / size);

        return PageProductResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    public ProductDto addReview(String id, ReviewCreateRequest req) {
        ProductDocument doc = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));

        ReviewDto review = ReviewDto.builder()
                .rating(req.getRating())
                .text(req.getText())
                .createdAt(Instant.now())
                .build();

        List<ReviewDto> reviews = doc.getReviews() != null ? new ArrayList<>(doc.getReviews()) : new ArrayList<>();
        reviews.add(0, review); // newest first

        // Embed at most 20 recent reviews
        if (reviews.size() > 20) {
            reviews = reviews.subList(0, 20);
        }

        int newCount = doc.getReviewCount() + 1;
        double currentTotal = doc.getRatingAvg() * doc.getReviewCount();
        double newAvg = Math.round(((currentTotal + req.getRating()) / newCount) * 10.0) / 10.0;

        doc.setReviews(reviews);
        doc.setReviewCount(newCount);
        doc.setRatingAvg(newAvg);
        doc.setUpdatedAt(Instant.now());

        ProductDocument saved = productRepository.save(doc);
        evictCatalogCache();
        return mapper.toDto(saved);
    }

    public ProductWithRecommendationsResponse getProductWithRecommendations(String id, long delayMs) {
        long start = System.currentTimeMillis();
        ProductDto product = getProductById(id);

        List<ProductDto> recs = Collections.emptyList();
        try {
            List<?> rawRecs = stubRestClient.get()
                    .uri("/stub/recommendations?delayMs={delayMs}&productId={id}", delayMs, id)
                    .retrieve()
                    .body(List.class);

            if (rawRecs != null) {
                recs = rawRecs.stream().map(r -> {
                    Map<?, ?> m = (Map<?, ?>) r;
                    return ProductDto.builder()
                            .id(String.valueOf(m.get("id")))
                            .name(String.valueOf(m.get("name")))
                            .build();
                }).toList();
            }
        } catch (Exception ex) {
            log.warn("stub-service call failed for product {}: {}", id, ex.getMessage());
        }

        long latency = System.currentTimeMillis() - start;
        return ProductWithRecommendationsResponse.builder()
                .product(product)
                .recommendations(recs)
                .latencyMs(latency)
                .build();
    }

    /** Kept for callers that already hold a document. */
    public ProductDto toDto(ProductDocument doc) {
        return mapper.toDto(doc);
    }

    private ProductDocument buildDocument(String id, ProductCreateRequest req) {
        return ProductDocument.builder()
                .id(id)
                .sku(req.getSku())
                .name(req.getName())
                .category(req.getCategory())
                .price(req.getPrice())
                .stock(req.getStock())
                .attributes(req.getAttributes() != null ? req.getAttributes() : new HashMap<>())
                .reviews(new ArrayList<>())
                .reviewCount(0)
                .ratingAvg(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void evictCatalogCache() {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
