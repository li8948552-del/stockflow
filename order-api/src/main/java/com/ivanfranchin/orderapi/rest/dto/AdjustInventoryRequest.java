package com.ivanfranchin.orderapi.rest.dto;

import static com.ivanfranchin.orderapi.inventory.InventoryMovement.REASON_MAX_LENGTH;
import static com.ivanfranchin.orderapi.inventory.InventoryMovement.REFERENCE_MAX_LENGTH;

import com.ivanfranchin.orderapi.validation.ValidBusinessText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AdjustInventoryRequest(
    @Schema(example = "-5") @NotNull Long quantityDelta,
    @Schema(example = "Cycle count correction")
        @ValidBusinessText(max = REASON_MAX_LENGTH, required = true)
        String reason,
    @Schema(example = "COUNT-2026-001") @ValidBusinessText(max = REFERENCE_MAX_LENGTH)
        String reference) {

  @AssertTrue(message = "quantityDelta must not be zero") public boolean isQuantityDeltaValid() {
    return quantityDelta == null || quantityDelta != 0;
  }
}
