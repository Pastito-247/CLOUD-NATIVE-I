package com.ecommerce.payments.service;

import com.ecommerce.payments.dto.OrderItemRequest;
import com.ecommerce.payments.dto.PaymentRequest;
import com.ecommerce.payments.event.OrderItemEvent;
import com.ecommerce.payments.event.PaymentRequestedEvent;
import com.ecommerce.payments.messaging.PaymentEventPublisher;
import com.ecommerce.payments.model.Payment;
import com.ecommerce.payments.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Logica de negocio de pagos. No conoce RabbitMQ mas alla de delegar en el
 * publicador; la simulacion de la pasarela vive en el consumidor de pantalla.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentEventPublisher publisher,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Registra el pago en estado PROCESSING y publica {@code payment.requested}
     * en el exchange fanout para disparar el flujo asincrono.
     */
    public Payment createPayment(PaymentRequest request) {
        List<OrderItemEvent> items = toEvents(request.items());
        String itemsJson = serializeItems(items);

        Payment payment = new Payment(
                request.orderId(),
                request.userEmail(),
                request.amount(),
                request.method(),
                request.shippingAddress(),
                itemsJson);
        Payment saved = paymentRepository.save(payment);

        publisher.publishPaymentRequested(new PaymentRequestedEvent(
                saved.getId(),
                saved.getOrderId(),
                saved.getUserEmail(),
                saved.getAmount(),
                saved.getMethod(),
                saved.getShippingAddress(),
                items));

        log.info("Pago {} registrado en estado {} para {}", saved.getId(), saved.getStatus(), saved.getUserEmail());
        return saved;
    }

    public Payment markCompleted(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Pago no encontrado: " + paymentId));
        payment.setStatus(Payment.STATUS_COMPLETED);
        return paymentRepository.save(payment);
    }

    public Payment markFailed(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Pago no encontrado: " + paymentId));
        payment.setStatus(Payment.STATUS_FAILED);
        return paymentRepository.save(payment);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<Payment> getPaymentsByUser(String userEmail) {
        return paymentRepository.findByUserEmail(userEmail);
    }

    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Pago no encontrado: " + id));
    }

    private List<OrderItemEvent> toEvents(List<OrderItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new OrderItemEvent(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.unitPrice()))
                .toList();
    }

    private String serializeItems(List<OrderItemEvent> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            log.warn("No se pudieron serializar los items del pago: {}", ex.getMessage());
            return "[]";
        }
    }
}
