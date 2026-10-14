package com.ecommerce.orders.service;

import com.ecommerce.orders.event.OrderItemEvent;
import com.ecommerce.orders.event.ReceiptGeneratedEvent;
import com.ecommerce.orders.model.OrderReview;
import com.ecommerce.orders.repository.OrderReviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Logica de negocio del pedido en revision. No conoce RabbitMQ: es invocada
 * desde el consumidor de {@code order.review.queue}.
 *
 * Para demostrar la ruta de error (reintentos y DLQ), un correo que empiece
 * con "fail@" provoca un fallo simulado.
 */
@Service
public class OrderReviewService {

    private static final Logger log = LoggerFactory.getLogger(OrderReviewService.class);

    private final OrderReviewRepository orderReviewRepository;
    private final ObjectMapper objectMapper;

    public OrderReviewService(OrderReviewRepository orderReviewRepository,
                              ObjectMapper objectMapper) {
        this.orderReviewRepository = orderReviewRepository;
        this.objectMapper = objectMapper;
    }

    public OrderReview saveForReview(ReceiptGeneratedEvent event) {
        if (event == null || event.receiptId() == null) {
            throw new IllegalArgumentException("Evento de boleta invalido: sin receiptId");
        }
        if (event.userEmail() != null && event.userEmail().startsWith("fail@")) {
            throw new IllegalStateException(
                    "Fallo simulado al registrar el pedido en revision (boleta " + event.receiptId() + ")");
        }

        OrderReview review = new OrderReview(
                event.orderId(),
                event.paymentId(),
                event.receiptId(),
                event.userEmail(),
                event.total(),
                serializeItems(event.items()));

        OrderReview saved = orderReviewRepository.save(review);
        log.info("Pedido {} registrado para revision (id={}, boleta {})",
                event.orderId(), saved.getId(), event.receiptId());
        return saved;
    }

    public List<OrderReview> getAllReviews() {
        return orderReviewRepository.findAll();
    }

    public List<OrderReview> getPendingReviews() {
        return orderReviewRepository.findByStatus(OrderReview.STATUS_PENDING);
    }

    private String serializeItems(List<OrderItemEvent> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            log.warn("No se pudieron serializar los items del pedido: {}", ex.getMessage());
            return "[]";
        }
    }
}
