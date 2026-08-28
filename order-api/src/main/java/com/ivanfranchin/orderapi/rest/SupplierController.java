package com.ivanfranchin.orderapi.rest;

import static com.ivanfranchin.orderapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

import com.ivanfranchin.orderapi.rest.dto.CreateSupplierRequest;
import com.ivanfranchin.orderapi.rest.dto.SupplierDto;
import com.ivanfranchin.orderapi.rest.dto.UpdateSupplierRequest;
import com.ivanfranchin.orderapi.supplier.SupplierService;
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
@RequestMapping("/api/suppliers")
public class SupplierController {
  private final SupplierService supplierService;

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping
  public List<SupplierDto> getSuppliers() {
    return supplierService.getSuppliers().stream().map(SupplierDto::from).toList();
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping("/{id}")
  public SupplierDto getSupplier(@PathVariable String id) {
    return SupplierDto.from(supplierService.getSupplier(id));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierDto createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
    return SupplierDto.from(supplierService.createSupplier(request.toDomain()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @PutMapping("/{id}")
  public SupplierDto updateSupplier(
      @PathVariable String id, @Valid @RequestBody UpdateSupplierRequest request) {
    return SupplierDto.from(supplierService.updateSupplier(id, request.toDomain()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateSupplier(@PathVariable String id) {
    supplierService.deactivateSupplier(id);
  }
}
