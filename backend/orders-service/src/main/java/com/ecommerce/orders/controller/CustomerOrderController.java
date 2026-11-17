package com.ecommerce.orders.controller;

import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.service.GuestOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/orders")
public class CustomerOrderController {

    private final GuestOrderService guestOrderService;

    public CustomerOrderController(GuestOrderService guestOrderService) {
        this.guestOrderService = guestOrderService;
    }

    @GetMapping
    public ResponseEntity<List<Order>> myOrders(Authentication authentication) {
        return ResponseEntity.ok(guestOrderService.findByUserEmail(resolveEmail(authentication)));
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
