package com.benchmark.nosqlreactive.repository;

import com.benchmark.nosqlreactive.domain.OrderDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends ReactiveMongoRepository<OrderDocument, String> {
}
