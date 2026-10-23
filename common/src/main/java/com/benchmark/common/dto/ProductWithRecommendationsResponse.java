package com.benchmark.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductWithRecommendationsResponse {
    private ProductDto product;
    private List<ProductDto> recommendations;
    private long latencyMs;
}
