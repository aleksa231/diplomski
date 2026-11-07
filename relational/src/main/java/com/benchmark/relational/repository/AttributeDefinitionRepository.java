package com.benchmark.relational.repository;

import com.benchmark.relational.domain.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, String> {

    Optional<AttributeDefinition> findByCategoryIdAndName(String categoryId, String name);
}
