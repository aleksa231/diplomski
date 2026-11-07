package com.benchmark.nosqlblocking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB representation of a cart, used when {@code app.cart.store=mongo}. Kept as a
 * single document (field = productId, value = quantity) so a cart is one read/write.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "carts")
public class CartDocument {
    @Id
    private String id;
    @Builder.Default
    private Map<String, Integer> items = new HashMap<>();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
