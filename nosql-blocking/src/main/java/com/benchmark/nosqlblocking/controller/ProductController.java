package com.benchmark.nosqlblocking.controller;

import com.benchmark.common.dto.*;
import com.benchmark.nosqlblocking.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<PageProductResponse> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Map<String, String> dynamicAttrs = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (k.startsWith("attr.") && v.length > 0) {
                dynamicAttrs.put(k.substring(5), v[0]);
            }
        });

        PageProductResponse resp = productService.findProducts(category, minPrice, maxPrice, dynamicAttrs, sort, page, size);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductDto created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable String id, @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Integer>> bulkInsert(@RequestBody List<ProductCreateRequest> requests) {
        int count = productService.bulkInsert(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("insertedCount", count));
    }

    @GetMapping("/search")
    public ResponseEntity<PageProductResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.searchProducts(q, page, size));
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<ProductDto> addReview(@PathVariable String id, @Valid @RequestBody ReviewCreateRequest request) {
        ProductDto updated = productService.addReview(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @GetMapping("/{id}/with-recommendations")
    public ResponseEntity<ProductWithRecommendationsResponse> getWithRecommendations(
            @PathVariable String id,
            @RequestParam(defaultValue = "200") long delayMs) {
        return ResponseEntity.ok(productService.getProductWithRecommendations(id, delayMs));
    }
}
