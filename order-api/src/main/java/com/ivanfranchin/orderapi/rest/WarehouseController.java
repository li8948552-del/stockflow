package com.ivanfranchin.orderapi.rest;

import static com.ivanfranchin.orderapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

import com.ivanfranchin.orderapi.rest.dto.CreateWarehouseRequest;
import com.ivanfranchin.orderapi.rest.dto.UpdateWarehouseRequest;
import com.ivanfranchin.orderapi.rest.dto.WarehouseDto;
import com.ivanfranchin.orderapi.warehouse.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
  private final WarehouseService warehouseService;

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping
  public List<WarehouseDto> getWarehouses() {
    return warehouseService.getWarehouses().stream().map(WarehouseDto::from).toList();
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping("/{id}")
  public WarehouseDto getWarehouse(@PathVariable String id) {
    return WarehouseDto.from(warehouseService.getWarehouse(id));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WarehouseDto createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
    return WarehouseDto.from(warehouseService.createWarehouse(request.toDomain()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @PutMapping("/{id}")
  public WarehouseDto updateWarehouse(
      @PathVariable String id, @Valid @RequestBody UpdateWarehouseRequest request) {
    return WarehouseDto.from(warehouseService.updateWarehouse(id, request.toDomain()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateWarehouse(@PathVariable String id) {
    warehouseService.deactivateWarehouse(id);
  }
}
