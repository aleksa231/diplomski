package com.benchmark.relational.repository;

import com.benchmark.relational.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEntityRepository extends JpaRepository<OrderEntity, String> {
}
