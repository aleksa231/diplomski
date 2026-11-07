package com.benchmark.relational.mapper;

import com.benchmark.common.dto.OrderDtos.OrderItemDto;
import com.benchmark.common.dto.OrderDtos.OrderResponse;
import com.benchmark.common.dto.OrderDtos.OrderStatusHistoryDto;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.common.dto.ReviewDto;
import com.benchmark.relational.domain.OrderEntity;
import com.benchmark.relational.domain.OrderStatusHistory;
import com.benchmark.relational.domain.Product;
import com.benchmark.relational.domain.ProductJsonb;
import com.benchmark.relational.domain.Review;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates the two relational product representations (normalized EAV and JSONB) plus the
 * order aggregate into the shared REST DTOs, preserving the JSON types of dynamic attributes.
 */
@Component
public class ProductMapper {

    public ProductDto toDto(Product product,
                            String categoryName,
                            Map<String, Object> attributes,
                            List<ReviewDto> reviews) {
        return ProductDto.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .category(categoryName)
                .price(product.getPrice() == null ? null : product.getPrice().doubleValue())
                .stock(product.getStock())
                .attributes(attributes == null ? new HashMap<>() : attributes)
                .reviews(reviews == null ? new ArrayList<>() : reviews)
                .reviewCount(product.getReviewCount())
                .ratingAvg(product.getRatingAvg() == null ? 0.0 : product.getRatingAvg().doubleValue())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public ProductDto toDto(ProductJsonb product, List<ReviewDto> reviews) {
        return ProductDto.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .category(product.getCategory())
                .price(product.getPrice() == null ? null : product.getPrice().doubleValue())
                .stock(product.getStock())
                .attributes(product.getAttributes() == null ? new HashMap<>() : product.getAttributes())
                .reviews(reviews == null ? new ArrayList<>() : reviews)
                .reviewCount(product.getReviewCount())
                .ratingAvg(product.getRatingAvg() == null ? 0.0 : product.getRatingAvg().doubleValue())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public ReviewDto toReviewDto(Review review) {
        return ReviewDto.builder()
                .rating(review.getRating())
                .text(review.getText())
                .createdAt(review.getCreatedAt())
                .build();
    }

    public OrderResponse toResponse(OrderEntity order) {
        List<OrderItemDto> items = order.getItems().stream()
                .map(i -> OrderItemDto.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .price(i.getPriceSnapshot() == null ? null : i.getPriceSnapshot().doubleValue())
                        .quantity(i.getQuantity())
                        .build())
                .toList();

        List<OrderStatusHistoryDto> history = order.getStatusHistory().stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getCreatedAt))
                .map(h -> OrderStatusHistoryDto.builder()
                        .status(h.getStatus())
                        .timestamp(h.getCreatedAt())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .customerRef(order.getCustomerRef())
                .items(items)
                .total(order.getTotal() == null ? 0.0 : order.getTotal().doubleValue())
                .status(order.getStatus())
                .statusHistory(history)
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * Converts the normalized EAV value back to the original JSON type: integral numbers become
     * {@link Integer} (the contract test asserts {@code ramGb -> 32} as an Integer), fractional
     * numbers become {@link Double}, booleans and strings are returned as-is.
     */
    public static Object typedValue(String stringValue, BigDecimal numberValue, Boolean booleanValue) {
        if (booleanValue != null) {
            return booleanValue;
        }
        if (numberValue != null) {
            try {
                return numberValue.intValueExact();
            } catch (ArithmeticException ex) {
                return numberValue.doubleValue();
            }
        }
        return stringValue;
    }
}
