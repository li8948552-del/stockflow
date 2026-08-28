package com.ivanfranchin.orderapi.rest.dto;

import static com.ivanfranchin.orderapi.inventory.InventoryMovement.REFERENCE_MAX_LENGTH;

import com.ivanfranchin.orderapi.validation.ValidBusinessText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReceiveInventoryRequest(
    @Schema(example = "product-id") @NotBlank String productId,
    @Schema(example = "warehouse-id") @NotBlank String warehouseId,
    @Schema(example = "100") @NotNull @Positive Long quantity,
    @Schema(example = "PO-2026-001") @ValidBusinessText(max = REFERENCE_MAX_LENGTH)
        String reference) {}
