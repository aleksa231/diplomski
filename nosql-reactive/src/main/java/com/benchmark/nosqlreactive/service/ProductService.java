package com.benchmark.nosqlreactive.service;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ProductWithRecommendationsResponse;
import com.benchmark.common.dto.ReviewCreateRequest;
import com.benchmark.common.dto.ReviewDto;
import com.benchmark.nosqlreactive.cache.QueryKeyFactory;
import com.benchmark.nosqlreactive.cache.ReactiveCatalogCache;
import com.benchmark.nosqlreactive.domain.ProductDocument;
import com.benchmark.nosqlreactive.mapper.ProductMapper;
import com.benchmark.nosqlreactive.metrics.BenchmarkMetrics;
import com.benchmark.nosqlreactive.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
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
    private final ReactiveMongoTemplate mongoTemplate;
    private final CatalogQueryService catalogQueryService;
    private final PopularityService popularityService;
    private final IndexAndExplainService indexAndExplainService;
    private final ProductMapper mapper;
    private final ReactiveCatalogCache catalogCache;
    private final WebClient stubWebClient;
    private final BenchmarkMetrics metrics;

    public ProductService(ProductRepository productRepository,
                          ReactiveMongoTemplate mongoTemplate,
                          CatalogQueryService catalogQueryService,
                          PopularityService popularityService,
                          IndexAndExplainService indexAndExplainService,
                          ProductMapper mapper,
                          ReactiveCatalogCache catalogCache,
                          WebClient stubWebClient,
                          BenchmarkMetrics metrics) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
        this.catalogQueryService = catalogQueryService;
        this.popularityService = popularityService;
        this.indexAndExplainService = indexAndExplainService;
        this.mapper = mapper;
        this.catalogCache = catalogCache;
        this.stubWebClient = stubWebClient;
        this.metrics = metrics;
    }

    /**
     * Catalog list query. The result is served from the Redis catalog cache when enabled;
     * the key is the normalized query hash.
     */
    public Mono<PageProductResponse> findProducts(String category,
                                                  Double minPrice,
                                                  Double maxPrice,
                                                  Map<String, String> dynamicAttrs,
                                                  String sortParam,
                                                  int page,
                                                  int size) {
        String cacheKey = QueryKeyFactory.catalogKey(category, minPrice, maxPrice, dynamicAttrs, sortParam, page, size);
        return catalogQueryService.loadPage(cacheKey, category, minPrice, maxPrice, dynamicAttrs, sortParam, page, size);
    }

    public Mono<ProductDto> getProductById(String id) {
        return metrics.time("mongo", "products.findById",
                        productRepository.findById(id)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Product not found with id: " + id))))
                .flatMap(doc -> popularityService.recordView(id).thenReturn(mapper.toDto(doc)));
    }

    public Mono<ProductDto> createProduct(ProductCreateRequest req) {
        String id = req.getId() != null && !req.getId().isBlank() ? req.getId() : UUID.randomUUID().toString();
        ProductDocument doc = buildDocument(id, req);
        return productRepository.save(doc)
                .flatMap(saved -> evictCatalogCache().thenReturn(mapper.toDto(saved)));
    }

    public Mono<ProductDto> updateProduct(String id, ProductCreateRequest req) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Product not found with id: " + id)))
                .flatMap(doc -> {
                    doc.setSku(req.getSku());
                    doc.setName(req.getName());
                    doc.setCategory(req.getCategory());
                    doc.setPrice(req.getPrice());
                    doc.setStock(req.getStock());
                    if (req.getAttributes() != null) {
                        doc.setAttributes(req.getAttributes());
                    }
                    doc.setUpdatedAt(Instant.now());
                    return productRepository.save(doc);
                })
                .flatMap(saved -> evictCatalogCache().thenReturn(mapper.toDto(saved)));
    }

    public Mono<Void> deleteProduct(String id) {
        return productRepository.existsById(id)
                .flatMap(exists -> {
                    if (!Boolean.TRUE.equals(exists)) {
                        return Mono.<Void>error(new NoSuchElementException("Product not found with id: " + id));
                    }
                    return productRepository.deleteById(id);
                })
                .then(evictCatalogCache());
    }

    public Mono<Integer> bulkInsert(List<ProductCreateRequest> requests) {
        List<ProductDocument> docs = new ArrayList<>(requests.size());
        for (ProductCreateRequest req : requests) {
            String id = req.getId() != null && !req.getId().isBlank() ? req.getId() : UUID.randomUUID().toString();
            docs.add(buildDocument(id, req));
        }
        return metrics.time("mongo", "products.bulkInsert", mongoTemplate.insertAll(docs).collectList())
                .flatMap(inserted -> evictCatalogCache().thenReturn(inserted.size()));
    }

    /**
     * Full-text search. Uses the {@code $text} operator when the experiment text index exists (S4 with
     * indexes enabled). MongoDB cannot run {@code $text} without that index, so with the indexes dropped
     * it degrades to a quoted regular-expression scan — the same "no index" baseline PostgreSQL gets.
     */
    public Mono<PageProductResponse> searchProducts(String q, int page, int size) {
        return indexAndExplainService.indexesEnabled()
                .flatMap(indexesEnabled -> {
                    Query query = buildSearchQuery(q, indexesEnabled);
                    return mongoTemplate.count(query, ProductDocument.class)
                            .flatMap(total -> {
                                Query pageQuery = buildSearchQuery(q, indexesEnabled);
                                pageQuery.skip((long) page * size).limit(size);
                                return mongoTemplate.find(pageQuery, ProductDocument.class)
                                        .map(mapper::toDto)
                                        .collectList()
                                        .map(content -> toResponse(content, page, size, total));
                            });
                });
    }

    public Mono<ProductDto> addReview(String id, ReviewCreateRequest req) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Product not found with id: " + id)))
                .flatMap(doc -> {
                    ReviewDto review = ReviewDto.builder()
                            .rating(req.getRating())
                            .text(req.getText())
                            .createdAt(Instant.now())
                            .build();

                    List<ReviewDto> reviews = doc.getReviews() != null ? new ArrayList<>(doc.getReviews()) : new ArrayList<>();
                    reviews.add(0, review); // newest first

                    // Embed at most 20 recent reviews
                    if (reviews.size() > 20) {
                        reviews = new ArrayList<>(reviews.subList(0, 20));
                    }

                    int newCount = doc.getReviewCount() + 1;
                    double currentTotal = doc.getRatingAvg() * doc.getReviewCount();
                    double newAvg = Math.round(((currentTotal + req.getRating()) / newCount) * 10.0) / 10.0;

                    doc.setReviews(reviews);
                    doc.setReviewCount(newCount);
                    doc.setRatingAvg(newAvg);
                    doc.setUpdatedAt(Instant.now());
                    return productRepository.save(doc);
                })
                .flatMap(saved -> evictCatalogCache().thenReturn(mapper.toDto(saved)));
    }

    public Mono<ProductWithRecommendationsResponse> getProductWithRecommendations(String id, long delayMs) {
        return Mono.defer(() -> {
            long start = System.currentTimeMillis();
            return getProductById(id).flatMap(product -> fetchRecommendations(id, delayMs)
                    .map(recs -> ProductWithRecommendationsResponse.builder()
                            .product(product)
                            .recommendations(recs)
                            .latencyMs(System.currentTimeMillis() - start)
                            .build()));
        });
    }

    private Mono<List<ProductDto>> fetchRecommendations(String id, long delayMs) {
        return stubWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/stub/recommendations")
                        .queryParam("delayMs", delayMs)
                        .queryParam("productId", id)
                        .build())
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .map(rec -> ProductDto.builder()
                        .id(String.valueOf(rec.get("id")))
                        .name(String.valueOf(rec.get("name")))
                        .build())
                .collectList()
                .onErrorResume(ex -> {
                    log.warn("stub-service call failed for product {}: {}", id, ex.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Query buildSearchQuery(String q, boolean indexesEnabled) {
        Query query = new Query();
        if (q != null && !q.isBlank()) {
            if (indexesEnabled) {
                query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(q.trim().split("\\s+")));
            } else {
                query.addCriteria(Criteria.where("name").regex(Pattern.quote(q), "i"));
            }
        }
        return query;
    }

    private static PageProductResponse toResponse(List<ProductDto> content, int page, int size, long total) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) total / size);
        return PageProductResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
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

    private Mono<Void> evictCatalogCache() {
        return catalogCache.clear();
    }
}
