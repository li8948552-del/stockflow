package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.inventory.Inventory;
import java.time.Instant;

public record InventoryDto(
    String inventoryId,
    String productId,
    String productSku,
    String productName,
    String warehouseId,
    String warehouseCode,
    String warehouseName,
    long onHand,
    long reserved,
    long available,
    long version,
    Instant updatedAt) {

  public static InventoryDto from(Inventory inventory) {
    return new InventoryDto(
        inventory.getId(),
        inventory.getProduct().getId(),
        inventory.getProduct().getSku(),
        inventory.getProduct().getName(),
        inventory.getWarehouse().getId(),
        inventory.getWarehouse().getWarehouseCode(),
        inventory.getWarehouse().getName(),
        inventory.getOnHand(),
        inventory.getReserved(),
        inventory.getAvailable(),
        inventory.getVersion(),
        inventory.getUpdatedAt());
  }
}
