package com.benchmark.relational.repository;

import com.benchmark.relational.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, String> {

    List<Review> findByProductIdOrderByCreatedAtDesc(String productId);
}
