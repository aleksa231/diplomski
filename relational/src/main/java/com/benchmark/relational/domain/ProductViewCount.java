package com.benchmark.relational.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Variant-agnostic view counter used by {@code GET /api/popular?type=views}. */
@Entity
@Table(name = "product_view_count")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductViewCount {

    @Id
    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(name = "views", nullable = false)
    private long views;
}
