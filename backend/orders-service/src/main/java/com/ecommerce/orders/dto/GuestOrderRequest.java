package com.ecommerce.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GuestOrderRequest(
        @NotBlank @Size(min = 2, max = 100) String customerName,
        @NotBlank @Email String customerEmail,
        @NotBlank @Size(min = 5, max = 200) String shippingAddress,
        @NotEmpty @Size(min = 1, max = 20) @Valid List<GuestOrderItemRequest> items
) {
}
