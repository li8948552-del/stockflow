package com.ivanfranchin.orderapi.rest;

import static com.ivanfranchin.orderapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

import com.ivanfranchin.orderapi.procurement.PurchaseOrderService;
import com.ivanfranchin.orderapi.procurement.PurchaseOrderStatus;
import com.ivanfranchin.orderapi.rest.dto.CreatePurchaseOrderRequest;
import com.ivanfranchin.orderapi.rest.dto.GoodsReceiptDto;
import com.ivanfranchin.orderapi.rest.dto.PurchaseOrderDto;
import com.ivanfranchin.orderapi.rest.dto.ReceivePurchaseOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
  private final PurchaseOrderService service;

  @Operation(security = @SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME))
  @GetMapping
  public List<PurchaseOrderDto> list(
      @RequestParam(required = false) String supplierId,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(required = false) PurchaseOrderStatus status) {
    return service.findAll(supplierId, warehouseId, status).stream()
        .map(PurchaseOrderDto::from)
        .toList();
  }

  @Operation(security = @SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME))
  @GetMapping("/{id}")
  public PurchaseOrderDto get(@PathVariable String id) {
    return PurchaseOrderDto.from(service.find(id));
  }

  @Operation(security = @SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME))
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public PurchaseOrderDto create(@Valid @RequestBody CreatePurchaseOrderRequest r) {
    return PurchaseOrderDto.from(service.create(r));
  }

  @PostMapping("/{id}/submit")
  public PurchaseOrderDto submit(@PathVariable String id) {
    return PurchaseOrderDto.from(service.submit(id));
  }

  @PostMapping("/{id}/receipts")
  public GoodsReceiptDto receive(
      @PathVariable String id,
      @Valid @RequestBody ReceivePurchaseOrderRequest r,
      org.springframework.security.core.Authentication a) {
    return GoodsReceiptDto.from(service.receive(id, r, a.getName()));
  }

  @PostMapping("/{id}/cancel")
  public PurchaseOrderDto cancel(@PathVariable String id) {
    return PurchaseOrderDto.from(service.cancel(id));
  }
}
