package com.ecommerce.orders.repository;

import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder(Order order);

    void deleteByOrder(Order order);
}
