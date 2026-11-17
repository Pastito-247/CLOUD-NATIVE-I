package com.ecommerce.orders.repository;

import com.ecommerce.orders.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByTrackingCode(String trackingCode);

    List<Order> findByUserEmail(String userEmail);

    List<Order> findByUserEmailIgnoreCase(String userEmail);
    List<Order> findByStatus(String status);
}
