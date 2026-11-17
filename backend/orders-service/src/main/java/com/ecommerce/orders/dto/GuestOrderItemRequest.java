package com.ecommerce.orders.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GuestOrderItemRequest(
        @NotNull Long productId,
        @Min(1) @Max(10) int quantity
) {
}
