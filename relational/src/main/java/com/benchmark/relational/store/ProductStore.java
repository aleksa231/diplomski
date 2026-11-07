package com.benchmark.relational.store;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ReviewCreateRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Storage abstraction for products and their stock. Two implementations are selected through
 * {@code app.relational.variant}: the normalized EAV model and the JSONB model. Cart, order and
 * report operations always use the shared relational tables, but every product read/write and
 * the stock decrement dispatch to the active variant.
 */
public interface ProductStore {

    String variant();

    ProductDto create(ProductCreateRequest request);

    Optional<ProductDto> findById(String id);

    ProductDto update(String id, ProductCreateRequest request);

    boolean deleteById(String id);

    int bulkInsert(List<ProductCreateRequest> requests);

    PageProductResponse query(String category,
                              Double minPrice,
                              Double maxPrice,
                              Map<String, String> dynamicAttrs,
                              String sortParam,
                              int page,
                              int size);

    PageProductResponse search(String q, int page, int size);

    ProductDto addReview(String id, ReviewCreateRequest request);

    /** Conditional {@code stock >= quantity} decrement; returns {@code true} when it applied. */
    boolean decrementStock(String productId, int quantity);
}
