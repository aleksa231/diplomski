package com.benchmark.relational.service;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ProductWithRecommendationsResponse;
import com.benchmark.common.dto.ReviewCreateRequest;
import com.benchmark.relational.cache.CacheConfig;
import com.benchmark.relational.cache.QueryKeyFactory;
import com.benchmark.relational.metrics.BenchmarkMetrics;
import com.benchmark.relational.store.ProductStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Product operations on top of the active {@link ProductStore} variant. Writes evict the
 * catalog cache so a stale page is never served after a mutation.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductStore productStore;
    private final CatalogQueryService catalogQueryService;
    private final PopularityService popularityService;
    private final CacheManager cacheManager;
    private final RestClient stubRestClient;
    private final BenchmarkMetrics metrics;

    public ProductService(ProductStore productStore,
                          CatalogQueryService catalogQueryService,
                          PopularityService popularityService,
                          CacheManager cacheManager,
                          RestClient stubRestClient,
                          BenchmarkMetrics metrics) {
        this.productStore = productStore;
        this.catalogQueryService = catalogQueryService;
        this.popularityService = popularityService;
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
        ProductDto product = metrics.time("postgres", "products.findById",
                () -> productStore.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id)));
        popularityService.recordView(id);
        return product;
    }

    public ProductDto createProduct(ProductCreateRequest req) {
        ProductDto created = metrics.time("postgres", "products.create", () -> productStore.create(req));
        evictCatalogCache();
        return created;
    }

    public ProductDto updateProduct(String id, ProductCreateRequest req) {
        ProductDto updated = metrics.time("postgres", "products.update", () -> productStore.update(id, req));
        evictCatalogCache();
        return updated;
    }

    public void deleteProduct(String id) {
        boolean deleted = metrics.time("postgres", "products.delete", () -> productStore.deleteById(id));
        if (!deleted) {
            throw new NoSuchElementException("Product not found with id: " + id);
        }
        evictCatalogCache();
    }

    public int bulkInsert(List<ProductCreateRequest> requests) {
        int inserted = metrics.time("postgres", "products.bulkInsert", () -> productStore.bulkInsert(requests));
        evictCatalogCache();
        return inserted;
    }

    public PageProductResponse searchProducts(String q, int page, int size) {
        return metrics.time("postgres", "products.search", () -> productStore.search(q, page, size));
    }

    public ProductDto addReview(String id, ReviewCreateRequest req) {
        ProductDto updated = metrics.time("postgres", "products.addReview", () -> productStore.addReview(id, req));
        evictCatalogCache();
        return updated;
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

    private void evictCatalogCache() {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
