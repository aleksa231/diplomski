package com.benchmark.nosqlblocking.repository;

import com.benchmark.nosqlblocking.domain.OrderDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends MongoRepository<OrderDocument, String> {
}
