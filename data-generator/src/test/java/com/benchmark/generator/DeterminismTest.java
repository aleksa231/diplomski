package com.benchmark.generator;

import com.benchmark.common.dto.OrderDtos;
import com.benchmark.generator.core.DataModelGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterminismTest {

    @Test
    void testSameSeedProducesIdenticalProductsAndOrders() {
        long seed = 42L;
        int productCount = 500;
        int orderCount = 1000;

        List<DataModelGenerator.GeneratedProduct> run1Products = DataModelGenerator.generateProducts(seed, productCount);
        List<OrderDtos.OrderResponse> run1Orders = DataModelGenerator.generateOrders(seed, orderCount, run1Products);

        List<DataModelGenerator.GeneratedProduct> run2Products = DataModelGenerator.generateProducts(seed, productCount);
        List<OrderDtos.OrderResponse> run2Orders = DataModelGenerator.generateOrders(seed, orderCount, run2Products);

        assertThat(run1Products).hasSize(productCount);
        assertThat(run2Products).hasSize(productCount);
        assertThat(run1Orders).hasSize(orderCount);
        assertThat(run2Orders).hasSize(orderCount);

        for (int i = 0; i < productCount; i++) {
            var p1 = run1Products.get(i).dto();
            var p2 = run2Products.get(i).dto();

            assertThat(p1.getId()).isEqualTo(p2.getId());
            assertThat(p1.getSku()).isEqualTo(p2.getSku());
            assertThat(p1.getName()).isEqualTo(p2.getName());
            assertThat(p1.getCategory()).isEqualTo(p2.getCategory());
            assertThat(p1.getPrice()).isEqualTo(p2.getPrice());
            assertThat(p1.getStock()).isEqualTo(p2.getStock());
            assertThat(p1.getAttributes()).isEqualTo(p2.getAttributes());
            assertThat(p1.getReviewCount()).isEqualTo(p2.getReviewCount());
            assertThat(p1.getRatingAvg()).isEqualTo(p2.getRatingAvg());
            assertThat(p1.getCreatedAt()).isEqualTo(p2.getCreatedAt());
        }

        for (int i = 0; i < orderCount; i++) {
            var o1 = run1Orders.get(i);
            var o2 = run2Orders.get(i);

            assertThat(o1.getId()).isEqualTo(o2.getId());
            assertThat(o1.getCustomerRef()).isEqualTo(o2.getCustomerRef());
            assertThat(o1.getTotal()).isEqualTo(o2.getTotal());
            assertThat(o1.getItems()).hasSameSizeAs(o2.getItems());
            for (int j = 0; j < o1.getItems().size(); j++) {
                assertThat(o1.getItems().get(j).getProductId()).isEqualTo(o2.getItems().get(j).getProductId());
                assertThat(o1.getItems().get(j).getQuantity()).isEqualTo(o2.getItems().get(j).getQuantity());
                assertThat(o1.getItems().get(j).getPrice()).isEqualTo(o2.getItems().get(j).getPrice());
            }
        }
    }

    @Test
    void testDifferentSeedProducesDifferentData() {
        List<DataModelGenerator.GeneratedProduct> run1 = DataModelGenerator.generateProducts(42L, 50);
        List<DataModelGenerator.GeneratedProduct> run2 = DataModelGenerator.generateProducts(99L, 50);

        assertThat(run1.get(0).dto().getId()).isNotEqualTo(run2.get(0).dto().getId());
        assertThat(run1.get(0).dto().getName()).isNotEqualTo(run2.get(0).dto().getName());
    }
}
