package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.inventory.InventoryMovement;
import com.ivanfranchin.orderapi.inventory.InventoryMovementType;
import java.time.Instant;

public record InventoryMovementDto(
    String id,
    String inventoryId,
    String productId,
    String productSku,
    String warehouseId,
    String warehouseCode,
    InventoryMovementType type,
    long quantityDelta,
    long onHandBefore,
    long onHandAfter,
    long reservedBefore,
    long reservedAfter,
    String reference,
    String reason,
    String createdBy,
    Instant createdAt) {

  public static InventoryMovementDto from(InventoryMovement movement) {
    return new InventoryMovementDto(
        movement.getId(),
        movement.getInventory().getId(),
        movement.getProduct().getId(),
        movement.getProduct().getSku(),
        movement.getWarehouse().getId(),
        movement.getWarehouse().getWarehouseCode(),
        movement.getType(),
        movement.getQuantityDelta(),
        movement.getOnHandBefore(),
        movement.getOnHandAfter(),
        movement.getReservedBefore(),
        movement.getReservedAfter(),
        movement.getReference(),
        movement.getReason(),
        movement.getCreatedBy(),
        movement.getCreatedAt());
  }
}
