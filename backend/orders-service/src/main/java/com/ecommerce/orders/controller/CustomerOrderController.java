package com.ecommerce.orders.controller;

import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.service.GuestOrderService;
import com.ecommerce.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/orders")
public class CustomerOrderController {

    private final GuestOrderService guestOrderService;
    private final OrderService orderService;

    public CustomerOrderController(GuestOrderService guestOrderService, OrderService orderService) {
        this.guestOrderService = guestOrderService;
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<Order>> myOrders(Authentication authentication) {
        return ResponseEntity.ok(guestOrderService.findByUserEmail(resolveEmail(authentication)));
    }

    @PostMapping
    public ResponseEntity<Order> createMyOrder(@Valid @RequestBody CreateOrderRequest request,
                                               Authentication authentication) {
        Order created = orderService.createOrder(request, resolveEmail(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> myOrderById(@PathVariable Long id,
                                             Authentication authentication) {
        String email = resolveEmail(authentication);
        return guestOrderService.findByUserEmail(email).stream()
                .filter(order -> order.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String resolveEmail(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            for (String claim : List.of("email", "preferred_username", "upn")) {
                String value = jwt.getClaimAsString(claim);
                if (value != null && !value.isBlank()) return value;
            }
            return jwt.getSubject();
        }
        return authentication.getName();
    }
}
