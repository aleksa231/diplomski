package com.benchmark.nosqlreactive.mapper;

import com.benchmark.common.dto.OrderDtos.OrderResponse;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.nosqlreactive.domain.OrderDocument;
import com.benchmark.nosqlreactive.domain.ProductDocument;
import org.springframework.stereotype.Component;

/**
 * Translates Mongo documents into the shared REST DTOs.
 */
@Component
public class ProductMapper {

    public ProductDto toDto(ProductDocument doc) {
        return ProductDto.builder()
                .id(doc.getId())
                .sku(doc.getSku())
                .name(doc.getName())
                .category(doc.getCategory())
                .price(doc.getPrice())
                .stock(doc.getStock())
                .attributes(doc.getAttributes())
                .reviews(doc.getReviews())
                .reviewCount(doc.getReviewCount())
                .ratingAvg(doc.getRatingAvg())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    public OrderResponse toResponse(OrderDocument doc) {
        return OrderResponse.builder()
                .id(doc.getId())
                .customerRef(doc.getCustomerRef())
                .items(doc.getItems())
                .total(doc.getTotal())
                .status(doc.getStatus())
                .statusHistory(doc.getStatusHistory())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
