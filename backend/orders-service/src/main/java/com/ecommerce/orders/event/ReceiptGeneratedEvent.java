package com.ecommerce.orders.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento de boleta generada (contrato compartido con payments-service).
 * Se consume desde {@code order.review.queue} para dejar el pedido listo para
 * revision del staff y administrador.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiptGeneratedEvent(
        Long receiptId,
        Long paymentId,
        Long orderId,
        String userEmail,
        BigDecimal total,
        String pdfPath,
        List<OrderItemEvent> items,
        LocalDateTime issuedAt
) {
}
