package com.benchmark.relational.repository;

import com.benchmark.relational.domain.ProductJsonb;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJsonbRepository extends JpaRepository<ProductJsonb, String> {
}
