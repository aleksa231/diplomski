package com.benchmark.relational.service;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.relational.cache.CacheConfig;
import com.benchmark.relational.metrics.BenchmarkMetrics;
import com.benchmark.relational.store.ProductStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Executes catalog list queries against PostgreSQL and caches the result in the
 * {@code products} Redis region. The cache key is the normalized, hashed query and the
 * entry TTL is 5 minutes (see CacheConfig).
 */
@Service
public class CatalogQueryService {

    private final ProductStore productStore;
    private final BenchmarkMetrics metrics;

    public CatalogQueryService(ProductStore productStore, BenchmarkMetrics metrics) {
        this.productStore = productStore;
        this.metrics = metrics;
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#p0")
    public PageProductResponse loadPage(String key,
                                        String category,
                                        Double minPrice,
                                        Double maxPrice,
                                        Map<String, String> dynamicAttrs,
                                        String sortParam,
                                        int page,
                                        int size) {
        return metrics.time("postgres", "products.query",
                () -> productStore.query(category, minPrice, maxPrice, dynamicAttrs, sortParam, page, size));
    }
}
