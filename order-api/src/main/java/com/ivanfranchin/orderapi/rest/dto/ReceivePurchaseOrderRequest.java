package com.ivanfranchin.orderapi.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReceivePurchaseOrderRequest(
    @NotBlank @Size(max = 128) String clientRequestId,
    @NotNull @NotEmpty List<@NotNull @Valid Item> items) {
  public record Item(
      String purchaseOrderItemId, Integer lineNumber, @NotNull @Positive Long quantity) {}
}
