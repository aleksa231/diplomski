package com.benchmark.relational.repository;

import com.benchmark.relational.domain.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, String> {

    List<ProductAttributeValue> findByProductId(String productId);

    void deleteByProductId(String productId);
}
