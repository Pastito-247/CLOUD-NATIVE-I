package com.ecommerce.orders.repository;

import com.ecommerce.orders.model.OrderReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderReviewRepository extends JpaRepository<OrderReview, Long> {

    List<OrderReview> findByStatus(String status);

    List<OrderReview> findByUserEmail(String userEmail);
}
