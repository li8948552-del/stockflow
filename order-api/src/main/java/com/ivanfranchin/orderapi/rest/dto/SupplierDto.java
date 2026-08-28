package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.supplier.Supplier;
import java.time.Instant;

public record SupplierDto(
    String id,
    String supplierCode,
    String name,
    String contactEmail,
    String phone,
    Integer leadTimeDays,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
  public static SupplierDto from(Supplier supplier) {
    return new SupplierDto(
        supplier.getId(),
        supplier.getSupplierCode(),
        supplier.getName(),
        supplier.getContactEmail(),
        supplier.getPhone(),
        supplier.getLeadTimeDays(),
        supplier.isActive(),
        supplier.getCreatedAt(),
        supplier.getUpdatedAt());
  }
}
