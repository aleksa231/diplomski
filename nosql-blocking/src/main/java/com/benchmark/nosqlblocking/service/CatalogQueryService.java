package com.benchmark.nosqlblocking.service;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.nosqlblocking.cache.CacheConfig;
import com.benchmark.nosqlblocking.domain.ProductDocument;
import com.benchmark.nosqlblocking.mapper.ProductMapper;
import com.benchmark.nosqlblocking.metrics.BenchmarkMetrics;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Executes catalog list queries against MongoDB and caches the result in the
 * {@code products} Redis region. The cache key is the normalized, hashed query and the
 * entry TTL is 5 minutes (see CacheConfig).
 */
@Service
public class CatalogQueryService {

    private final MongoTemplate mongoTemplate;
    private final ProductMapper mapper;
    private final BenchmarkMetrics metrics;

    public CatalogQueryService(MongoTemplate mongoTemplate, ProductMapper mapper, BenchmarkMetrics metrics) {
        this.mongoTemplate = mongoTemplate;
        this.mapper = mapper;
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
        return metrics.time("mongo", "products.query", () -> execute(category, minPrice, maxPrice, dynamicAttrs, sortParam, page, size));
    }

    private PageProductResponse execute(String category,
                                        Double minPrice,
                                        Double maxPrice,
                                        Map<String, String> dynamicAttrs,
                                        String sortParam,
                                        int page,
                                        int size) {
        Query query = new Query();

        if (category != null && !category.isBlank()) {
            query.addCriteria(Criteria.where("category").is(category));
        }

        if (minPrice != null || maxPrice != null) {
            Criteria priceCriteria = Criteria.where("price");
            if (minPrice != null) priceCriteria = priceCriteria.gte(minPrice);
            if (maxPrice != null) priceCriteria = priceCriteria.lte(maxPrice);
            query.addCriteria(priceCriteria);
        }

        if (dynamicAttrs != null) {
            for (Map.Entry<String, String> entry : dynamicAttrs.entrySet()) {
                query.addCriteria(Criteria.where("attributes." + entry.getKey()).is(parseAttributeValue(entry.getValue())));
            }
        }

        long total = mongoTemplate.count(query, ProductDocument.class);

        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",");
            String sortProp = parts[0].trim();
            Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            query.with(Sort.by(dir, sortProp));
        }

        query.skip((long) page * size).limit(size);
        List<ProductDocument> docs = mongoTemplate.find(query, ProductDocument.class);

        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) total / size);
        return PageProductResponse.builder()
                .content(docs.stream().map(mapper::toDto).toList())
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    private Object parseAttributeValue(String val) {
        if (val == null) {
            return null;
        }
        if (val.equalsIgnoreCase("true")) {
            return true;
        }
        if (val.equalsIgnoreCase("false")) {
            return false;
        }
        try {
            return val.contains(".") ? Double.parseDouble(val) : Long.parseLong(val);
        } catch (NumberFormatException ignored) {
            return val;
        }
    }
}
