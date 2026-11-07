package com.benchmark.relational.repository;

import com.benchmark.relational.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    Optional<Product> findBySku(String sku);
}
