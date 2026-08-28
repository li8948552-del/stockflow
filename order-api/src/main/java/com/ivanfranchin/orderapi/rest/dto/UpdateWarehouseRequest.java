package com.ivanfranchin.orderapi.rest.dto;

import static com.ivanfranchin.orderapi.warehouse.Warehouse.CODE_MAX_LENGTH;
import static com.ivanfranchin.orderapi.warehouse.Warehouse.LOCATION_MAX_LENGTH;
import static com.ivanfranchin.orderapi.warehouse.Warehouse.NAME_MAX_LENGTH;

import com.ivanfranchin.orderapi.validation.ValidBusinessCode;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWarehouseRequest(
    @ValidBusinessCode(max = CODE_MAX_LENGTH) String warehouseCode,
    @NotBlank @Size(max = NAME_MAX_LENGTH) String name,
    @NotBlank @Size(max = LOCATION_MAX_LENGTH) String location,
    @NotNull Boolean active) {
  public Warehouse toDomain() {
    Warehouse warehouse = new Warehouse(warehouseCode, name, location);
    warehouse.setActive(active);
    return warehouse;
  }
}
