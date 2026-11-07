package com.benchmark.nosqlreactive.controller;

import com.benchmark.common.dto.PageProductResponse;
import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ProductWithRecommendationsResponse;
import com.benchmark.common.dto.ReviewCreateRequest;
import com.benchmark.nosqlreactive.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Mono<ResponseEntity<PageProductResponse>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            ServerWebExchange exchange) {

        Map<String, String> dynamicAttrs = new HashMap<>();
        exchange.getRequest().getQueryParams().forEach((k, values) -> {
            if (k.startsWith("attr.") && !values.isEmpty()) {
                dynamicAttrs.put(k.substring(5), values.get(0));
            }
        });

        return productService.findProducts(category, minPrice, maxPrice, dynamicAttrs, sort, page, size)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ProductDto>> getProductById(@PathVariable String id) {
        return productService.getProductById(id).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<ProductDto>> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return productService.createProduct(request)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ProductDto>> updateProduct(@PathVariable String id,
                                                          @Valid @RequestBody ProductCreateRequest request) {
        return productService.updateProduct(id, request).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProduct(@PathVariable String id) {
        return productService.deleteProduct(id)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/bulk")
    public Mono<ResponseEntity<Map<String, Integer>>> bulkInsert(@RequestBody List<ProductCreateRequest> requests) {
        return productService.bulkInsert(requests)
                .map(count -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of("insertedCount", count)));
    }

    @GetMapping("/search")
    public Mono<ResponseEntity<PageProductResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.searchProducts(q, page, size).map(ResponseEntity::ok);
    }

    @PostMapping("/{id}/reviews")
    public Mono<ResponseEntity<ProductDto>> addReview(@PathVariable String id,
                                                      @Valid @RequestBody ReviewCreateRequest request) {
        return productService.addReview(id, request)
                .map(updated -> ResponseEntity.status(HttpStatus.CREATED).body(updated));
    }

    @GetMapping("/{id}/with-recommendations")
    public Mono<ResponseEntity<ProductWithRecommendationsResponse>> getWithRecommendations(
            @PathVariable String id,
            @RequestParam(defaultValue = "200") long delayMs) {
        return productService.getProductWithRecommendations(id, delayMs).map(ResponseEntity::ok);
    }
}
