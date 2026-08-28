package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.warehouse.Warehouse;
import java.time.Instant;

public record WarehouseDto(
    String id,
    String warehouseCode,
    String name,
    String location,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
  public static WarehouseDto from(Warehouse warehouse) {
    return new WarehouseDto(
        warehouse.getId(),
        warehouse.getWarehouseCode(),
        warehouse.getName(),
        warehouse.getLocation(),
        warehouse.isActive(),
        warehouse.getCreatedAt(),
        warehouse.getUpdatedAt());
  }
}
