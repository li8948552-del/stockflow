package com.ivanfranchin.orderapi.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseOrderRequest(
    @NotBlank String supplierId,
    @NotBlank String warehouseId,
    LocalDate expectedDeliveryDate,
    @NotNull @NotEmpty List<@NotNull @Valid Item> items) {
  public record Item(
      @NotBlank String productId,
      @NotNull @Positive Long quantity,
      @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal unitCost) {}
}
