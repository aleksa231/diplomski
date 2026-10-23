package com.benchmark.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private String id;
    private String sku;
    private String name;
    private String category;
    private Double price;
    private Integer stock;
    private Map<String, Object> attributes;
    private List<ReviewDto> reviews;
    private Integer reviewCount;
    private Double ratingAvg;
    private Instant createdAt;
    private Instant updatedAt;
}
