package com.ecommerce.orders.controller;

import com.ecommerce.orders.dto.GuestOrderRequest;
import com.ecommerce.orders.dto.OrderConfirmation;
import com.ecommerce.orders.service.GuestOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public/orders")
public class PublicOrderController {

    private final GuestOrderService guestOrderService;

    public PublicOrderController(GuestOrderService guestOrderService) {
        this.guestOrderService = guestOrderService;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody GuestOrderRequest request) {
        try {
            OrderConfirmation confirmation = guestOrderService.createGuestOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(confirmation);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{trackingCode}")
    public ResponseEntity<?> findByTrackingCode(@PathVariable String trackingCode) {
        return guestOrderService.findPublicByTrackingCode(trackingCode)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Pedido no encontrado")));
    }
}
