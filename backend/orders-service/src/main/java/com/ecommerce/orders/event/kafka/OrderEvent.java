package com.ecommerce.orders.event.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record OrderEvent(
        String eventId,
        String eventType,
        Long orderId,
        String trackingCode,
        String userEmail,
        boolean guest,
        BigDecimal total,
        String status,
        Instant occurredAt,
        String source,
        Map<String, Object> metadata
) {
}
