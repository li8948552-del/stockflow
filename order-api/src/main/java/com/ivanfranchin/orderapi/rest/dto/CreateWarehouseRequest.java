package com.ivanfranchin.orderapi.rest.dto;

import static com.ivanfranchin.orderapi.warehouse.Warehouse.CODE_MAX_LENGTH;
import static com.ivanfranchin.orderapi.warehouse.Warehouse.LOCATION_MAX_LENGTH;
import static com.ivanfranchin.orderapi.warehouse.Warehouse.NAME_MAX_LENGTH;

import com.ivanfranchin.orderapi.validation.ValidBusinessCode;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
    @ValidBusinessCode(max = CODE_MAX_LENGTH) String warehouseCode,
    @NotBlank @Size(max = NAME_MAX_LENGTH) String name,
    @NotBlank @Size(max = LOCATION_MAX_LENGTH) String location) {
  public Warehouse toDomain() {
    return new Warehouse(warehouseCode, name, location);
  }
}
