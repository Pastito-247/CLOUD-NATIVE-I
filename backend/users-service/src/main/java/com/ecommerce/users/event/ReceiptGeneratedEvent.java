package com.ecommerce.users.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento de boleta generada (contrato compartido con payments-service).
 * Se consume desde {@code receipt.email.queue} para enviar la confirmacion
 * del pedido junto con la boleta.
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
