package com.benchmark.relational.repository;

import com.benchmark.relational.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, String> {

    List<CartItem> findByCartId(String cartId);

    Optional<CartItem> findByCartIdAndProductId(String cartId, String productId);

    void deleteByCartIdAndProductId(String cartId, String productId);
}
