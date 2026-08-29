package com.ivanfranchin.orderapi.rest;

import static com.ivanfranchin.orderapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.rest.dto.AdjustInventoryRequest;
import com.ivanfranchin.orderapi.rest.dto.InventoryDto;
import com.ivanfranchin.orderapi.rest.dto.InventoryMovementDto;
import com.ivanfranchin.orderapi.rest.dto.ReceiveInventoryRequest;
import com.ivanfranchin.orderapi.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  private final InventoryService inventoryService;

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping
  public List<InventoryDto> getInventory(
      @RequestParam(required = false) String productId,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(defaultValue = "false") boolean lowStock) {
    return inventoryService.getInventory(productId, warehouseId, lowStock).stream()
        .map(InventoryDto::from)
        .toList();
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping("/{id}")
  public InventoryDto getInventory(@PathVariable String id) {
    return InventoryDto.from(inventoryService.getInventory(id));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/receipts")
  public InventoryDto receive(
      @Valid @RequestBody ReceiveInventoryRequest request,
      @AuthenticationPrincipal CustomUserDetails currentUser) {
    return InventoryDto.from(
        inventoryService.receive(
            request.productId(),
            request.warehouseId(),
            request.quantity(),
            request.reference(),
            currentUser.getUsername()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @PostMapping("/{id}/adjustments")
  public InventoryDto adjust(
      @PathVariable String id,
      @Valid @RequestBody AdjustInventoryRequest request,
      @AuthenticationPrincipal CustomUserDetails currentUser) {
    return InventoryDto.from(
        inventoryService.adjust(
            id,
            request.quantityDelta(),
            request.reason(),
            request.reference(),
            currentUser.getUsername()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping("/{id}/movements")
  public List<InventoryMovementDto> getMovements(@PathVariable String id) {
    return inventoryService.getMovements(id).stream().map(InventoryMovementDto::from).toList();
  }
}
