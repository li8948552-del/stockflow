package com.ivanfranchin.orderapi.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
    @NotBlank String productId, @NotNull @Positive Long quantity) {}
