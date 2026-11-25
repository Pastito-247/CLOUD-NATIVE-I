package com.ecommerce.payments.controller;

import com.ecommerce.payments.client.CustomerOrderClient;
import com.ecommerce.payments.dto.PaymentRequest;
import com.ecommerce.payments.model.Payment;
import com.ecommerce.payments.security.JwtEmailResolver;
import com.ecommerce.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/customer/payments")
public class CustomerPaymentController {

    private final PaymentService paymentService;
    private final CustomerOrderClient customerOrderClient;

    public CustomerPaymentController(PaymentService paymentService,
                                     CustomerOrderClient customerOrderClient) {
        this.paymentService = paymentService;
        this.customerOrderClient = customerOrderClient;
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            Authentication authentication) {
        String email = JwtEmailResolver.resolve(authentication);
        if (!customerOrderClient.ownsOrder(request.orderId(), authorization)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La orden no pertenece al cliente");
        }
        Payment payment = paymentService.createPayment(request, email);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(payment);
    }

    @GetMapping("/my-payments")
    public ResponseEntity<List<Payment>> myPayments(Authentication authentication) {
        return ResponseEntity.ok(
                paymentService.getPaymentsByUser(JwtEmailResolver.resolve(authentication)));
    }
}
