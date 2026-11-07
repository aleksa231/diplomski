package com.benchmark.nosqlreactive.service;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.nosqlreactive.cache.ReactiveCatalogCache;
import com.benchmark.nosqlreactive.domain.ProductDocument;
import com.benchmark.nosqlreactive.mapper.ProductMapper;
import com.benchmark.nosqlreactive.metrics.BenchmarkMetrics;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Executes catalog list queries against MongoDB and caches the result in the reactive
 * {@code products} Redis region. The cache key is the normalized, hashed query and the
 * entry TTL is 5 minutes (see CacheConfig).
 */
@Service
public class CatalogQueryService {

    private final ReactiveMongoTemplate mongoTemplate;
    private final ProductMapper mapper;
    private final BenchmarkMetrics metrics;
    private final ReactiveCatalogCache cache;

    public CatalogQueryService(ReactiveMongoTemplate mongoTemplate,
                               ProductMapper mapper,
                               BenchmarkMetrics metrics,
                               ReactiveCatalogCache cache) {
        this.mongoTemplate = mongoTemplate;
        this.mapper = mapper;
        this.metrics = metrics;
        this.cache = cache;
    }

    public Mono<PageProductResponse> loadPage(String key,
                                              String category,
                                              Double minPrice,
                                              Double maxPrice,
                                              Map<String, String> dynamicAttrs,
                                              String sortParam,
                                              int page,
                                              int size) {
        return cache.get(key)
                .switchIfEmpty(Mono.defer(() -> metrics.time("mongo", "products.query",
                                execute(category, minPrice, maxPrice, dynamicAttrs, sortParam, page, size))
                        .flatMap(response -> cache.put(key, response).thenReturn(response))));
    }

    private Mono<PageProductResponse> execute(String category,
                                              Double minPrice,
                                              Double maxPrice,
                                              Map<String, String> dynamicAttrs,
                                              String sortParam,
                                              int page,
                                              int size) {
        Query query = buildQuery(category, minPrice, maxPrice, dynamicAttrs);
        return mongoTemplate.count(query, ProductDocument.class)
                .flatMap(total -> {
                    Query pageQuery = buildQuery(category, minPrice, maxPrice, dynamicAttrs);
                    applySort(pageQuery, sortParam);
                    pageQuery.skip((long) page * size).limit(size);
                    return mongoTemplate.find(pageQuery, ProductDocument.class)
                            .map(mapper::toDto)
                            .collectList()
                            .map(content -> toResponse(content, page, size, total));
                });
    }

    private static Query buildQuery(String category,
                                    Double minPrice,
                                    Double maxPrice,
                                    Map<String, String> dynamicAttrs) {
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
                query.addCriteria(Criteria.where("attributes." + entry.getKey())
                        .is(parseAttributeValue(entry.getValue())));
            }
        }

        return query;
    }

    private static void applySort(Query query, String sortParam) {
        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",");
            String sortProp = parts[0].trim();
            Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            query.with(Sort.by(dir, sortProp));
        }
    }

    private static PageProductResponse toResponse(List<ProductDto> content,
                                                  int page,
                                                  int size,
                                                  long total) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) total / size);
        return PageProductResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    private static Object parseAttributeValue(String val) {
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
