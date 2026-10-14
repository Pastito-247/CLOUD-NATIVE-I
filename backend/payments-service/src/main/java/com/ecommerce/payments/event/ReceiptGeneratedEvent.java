package com.ecommerce.payments.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento publicado cuando el pago se proceso y se genero la boleta PDF.
 *
 * Es el contrato de mensajeria del exchange {@code receipt.exchange} (fanout):
 * lo consumen las colas de correo de confirmacion, revision del pedido
 * (staff/admin) y descuento de stock.
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
