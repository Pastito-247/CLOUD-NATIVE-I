package com.ecommerce.payments.event.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record PaymentEvent(
        String eventId,
        String eventType,
        Long paymentId,
        Long orderId,
        String userEmail,
        BigDecimal amount,
        String status,
        Instant occurredAt,
        String source,
        Map<String, Object> metadata
) {
}
