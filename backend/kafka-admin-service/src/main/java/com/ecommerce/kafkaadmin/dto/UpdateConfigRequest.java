package com.ecommerce.kafkaadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateConfigRequest(
        @NotBlank String key,
        @NotBlank String value
) {
}
