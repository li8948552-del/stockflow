package com.ivanfranchin.orderapi.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
    @NotBlank String warehouseId,
    @NotNull @NotEmpty List<@NotNull @Valid CreateOrderItemRequest> items) {}
