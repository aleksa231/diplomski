package com.benchmark.relational.repository;

import com.benchmark.relational.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface CartRepository extends JpaRepository<Cart, String> {

    @Modifying
    @Query("delete from Cart c where c.expiresAt < :now")
    int deleteExpired(Instant now);
}
