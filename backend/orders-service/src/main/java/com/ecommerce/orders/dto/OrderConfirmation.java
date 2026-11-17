package com.ecommerce.orders.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderConfirmation(
        String trackingCode,
        String status,
        BigDecimal total,
        int itemCount,
        LocalDateTime createdAt
) {
}
