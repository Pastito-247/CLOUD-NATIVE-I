package com.ecommerce.orders.controller;

import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.security.JwtEmailResolver;
import com.ecommerce.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    // Private endpoints - require authentication
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id, Authentication authentication) {
        Order order = orderService.getOrderById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        JwtEmailResolver.requireOwnerOrAdmin(authentication, order.getUserEmail());
        return ResponseEntity.ok(order);
    }

    @GetMapping("/user/{email}")
    public ResponseEntity<List<Order>> getOrdersByUserEmail(@PathVariable String email,
                                                            Authentication authentication) {
        JwtEmailResolver.requireOwnerOrAdmin(authentication, email);
        return ResponseEntity.ok(orderService.getOrdersByUserEmail(email));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<List<Order>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(orderService.getOrdersByUserEmail(JwtEmailResolver.resolve(authentication)));
    }


    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<List<Order>> getOrdersByStatus(@PathVariable String status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status));
    }
    
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                             Authentication authentication) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(orderService.createOrder(request, JwtEmailResolver.resolve(authentication)));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order order) {
        try {
            return ResponseEntity.ok(orderService.updateOrder(id, order));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        try {
            orderService.deleteOrder(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
