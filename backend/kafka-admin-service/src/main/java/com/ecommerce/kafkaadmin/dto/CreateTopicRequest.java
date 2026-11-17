package com.ecommerce.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateTopicRequest(
        @NotBlank String name,
        @Min(1) @Max(100) int partitions,
        @Min(1) @Max(10) short replicationFactor,
        Long retentionMs
) {
}
