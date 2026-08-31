package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.procurement.PurchaseOrder;
import com.ivanfranchin.orderapi.procurement.PurchaseOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonSerialize;

public record PurchaseOrderDto(
    String id,
    String supplierId,
    String supplierCode,
    String supplierName,
    String warehouseId,
    String warehouseCode,
    String warehouseName,
    PurchaseOrderStatus status,
    @JsonSerialize(using = PlainBigDecimalSerializer.class)
        @Schema(type = "string", format = "decimal")
        BigDecimal totalAmount,
    LocalDate expectedDeliveryDate,
    Instant submittedAt,
    Instant cancelledAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    Long version,
    List<Item> items) {
  public record Item(
      String id,
      String productId,
      String productSku,
      String productName,
      int lineNumber,
      long orderedQuantity,
      long receivedQuantity,
      long remainingQuantity,
      @JsonSerialize(using = PlainBigDecimalSerializer.class) BigDecimal unitCost,
      @JsonSerialize(using = PlainBigDecimalSerializer.class) BigDecimal lineTotal) {}

  public static PurchaseOrderDto from(PurchaseOrder p) {
    return new PurchaseOrderDto(
        p.getId(),
        p.getSupplier().getId(),
        p.getSupplier().getSupplierCode(),
        p.getSupplier().getName(),
        p.getWarehouse().getId(),
        p.getWarehouse().getWarehouseCode(),
        p.getWarehouse().getName(),
        p.getStatus(),
        p.getTotalAmount(),
        p.getExpectedDeliveryDate(),
        p.getSubmittedAt(),
        p.getCancelledAt(),
        p.getCompletedAt(),
        p.getCreatedAt(),
        p.getUpdatedAt(),
        p.getVersion(),
        p.getItems().stream()
            .map(
                i ->
                    new Item(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getSku(),
                        i.getProduct().getName(),
                        i.getLineNumber(),
                        i.getOrderedQuantity(),
                        i.getReceivedQuantity(),
                        i.getRemainingQuantity(),
                        i.getUnitCost(),
                        i.getLineTotal()))
            .toList());
  }
}
