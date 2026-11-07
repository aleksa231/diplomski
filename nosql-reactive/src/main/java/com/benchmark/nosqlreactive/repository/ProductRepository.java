package com.benchmark.nosqlreactive.repository;

import com.benchmark.nosqlreactive.domain.ProductDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ProductRepository extends ReactiveMongoRepository<ProductDocument, String> {
    Mono<ProductDocument> findBySku(String sku);
}
