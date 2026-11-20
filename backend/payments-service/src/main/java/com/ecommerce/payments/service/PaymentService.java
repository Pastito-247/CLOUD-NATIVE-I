package com.ecommerce.payments.service;

import com.ecommerce.payments.client.ProductCatalogClient;
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

import java.math.BigDecimal;
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
    private final com.ecommerce.payments.messaging.kafka.PaymentEventPublisher kafkaPublisher;
    private final ObjectMapper objectMapper;
    private final ProductCatalogClient productCatalogClient;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentEventPublisher publisher,
                          com.ecommerce.payments.messaging.kafka.PaymentEventPublisher kafkaPublisher,
                          ObjectMapper objectMapper,
                          ProductCatalogClient productCatalogClient) {
        this.paymentRepository = paymentRepository;
        this.publisher = publisher;
        this.kafkaPublisher = kafkaPublisher;
        this.objectMapper = objectMapper;
        this.productCatalogClient = productCatalogClient;
    }

    /**
     * Registra el pago en estado PROCESSING y publica {@code payment.requested}
     * en el exchange fanout para disparar el flujo asincrono.
     */
    public Payment createPayment(PaymentRequest request, String userEmail) {
        List<OrderItemEvent> items = toEvents(request.items());
        BigDecimal amount = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("No se pudo calcular el importe del pago");
        }
        String itemsJson = serializeItems(items);

        Payment payment = new Payment(
                request.orderId(),
                userEmail,
                amount,
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

        kafkaPublisher.publishPaymentEvent(
                saved.getId(), saved.getOrderId(), saved.getUserEmail(),
                saved.getAmount(), saved.getStatus(), "PAYMENT_REQUESTED");

        log.info("Pago {} registrado en estado {} para {}", saved.getId(), saved.getStatus(), saved.getUserEmail());
        return saved;
    }

    public Payment markCompleted(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Pago no encontrado: " + paymentId));
        payment.setStatus(Payment.STATUS_COMPLETED);
        Payment saved = paymentRepository.save(payment);
        kafkaPublisher.publishPaymentEvent(
                saved.getId(), saved.getOrderId(), saved.getUserEmail(),
                saved.getAmount(), saved.getStatus(), "PAYMENT_APPROVED");
        return saved;
    }

    public Payment markFailed(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Pago no encontrado: " + paymentId));
        payment.setStatus(Payment.STATUS_FAILED);
        Payment saved = paymentRepository.save(payment);
        kafkaPublisher.publishPaymentEvent(
                saved.getId(), saved.getOrderId(), saved.getUserEmail(),
                saved.getAmount(), saved.getStatus(), "PAYMENT_REJECTED");
        return saved;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<Payment> getPaymentsByUser(String userEmail) {
        return paymentRepository.findByUserEmailIgnoreCase(userEmail);
    }

    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Pago no encontrado: " + id));
    }

    private List<OrderItemEvent> toEvents(List<OrderItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(item -> {
            ProductCatalogClient.ProductInfo product = productCatalogClient.getProduct(item.productId());
            if (product == null || product.price() == null || product.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Producto no disponible: " + item.productId());
            }
            return new OrderItemEvent(item.productId(), product.name(), item.quantity(), product.price());
        }).toList();
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
