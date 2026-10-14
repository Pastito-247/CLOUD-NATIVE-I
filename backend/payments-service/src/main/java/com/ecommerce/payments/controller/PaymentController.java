package com.ecommerce.payments.controller;

import com.ecommerce.payments.dto.PaymentRequest;
import com.ecommerce.payments.model.Payment;
import com.ecommerce.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de pago.
 *
 * {@code POST /api/payments} registra el pago (simulado) y dispara el flujo
 * asincrono: payment.exchange (fanout) -> notificacion + simulacion/boleta.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@Valid @RequestBody PaymentRequest request) {
        Payment payment = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(payment);
    }

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<List<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @GetMapping("/my-payments")
    public ResponseEntity<List<Payment>> getMyPayments(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(paymentService.getPaymentsByUser(email));
    }
}
