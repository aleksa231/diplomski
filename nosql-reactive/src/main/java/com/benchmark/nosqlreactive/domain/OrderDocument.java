package com.benchmark.nosqlreactive.domain;

import com.benchmark.common.dto.OrderDtos;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class OrderDocument {
    @Id
    private String id;
    private String customerRef;
    @Builder.Default
    private List<OrderDtos.OrderItemDto> items = new ArrayList<>();
    private Double total;
    private String status;
    @Builder.Default
    private List<OrderDtos.OrderStatusHistoryDto> statusHistory = new ArrayList<>();
    @Builder.Default
    private Instant createdAt = Instant.now();
}
