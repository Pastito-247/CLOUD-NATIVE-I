package com.ecommerce.orders.controller;

import com.ecommerce.orders.model.OrderReview;
import com.ecommerce.orders.service.OrderReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de consulta de los pedidos en revision (staff/admin).
 */
@RestController
@RequestMapping("/api/orders/review")
public class OrderReviewController {

    private final OrderReviewService orderReviewService;

    public OrderReviewController(OrderReviewService orderReviewService) {
        this.orderReviewService = orderReviewService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('admin','staff')")
    public ResponseEntity<List<OrderReview>> getAllReviews() {
        return ResponseEntity.ok(orderReviewService.getAllReviews());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('admin','staff')")
    public ResponseEntity<List<OrderReview>> getPendingReviews() {
        return ResponseEntity.ok(orderReviewService.getPendingReviews());
    }
}
