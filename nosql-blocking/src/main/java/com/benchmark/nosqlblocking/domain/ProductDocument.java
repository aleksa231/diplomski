package com.benchmark.nosqlblocking.domain;

import com.benchmark.common.dto.ReviewDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class ProductDocument {
    @Id
    private String id;
    private String sku;
    private String name;
    private String category;
    private Double price;
    private Integer stock;
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
    @Builder.Default
    private List<ReviewDto> reviews = new ArrayList<>();
    @Builder.Default
    private Integer reviewCount = 0;
    @Builder.Default
    private Double ratingAvg = 0.0;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
