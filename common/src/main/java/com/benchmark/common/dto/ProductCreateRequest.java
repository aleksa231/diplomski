package com.benchmark.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {
    private String id; // Optional when client generates IDs (e.g. data generator)
    @NotBlank
    private String sku;
    @NotBlank
    private String name;
    @NotBlank
    private String category;
    @NotNull
    @Min(0)
    private Double price;
    @NotNull
    @Min(0)
    private Integer stock;
    private Map<String, Object> attributes;
}
