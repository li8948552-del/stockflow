package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.product.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductDto(
    String id,
    String sku,
    String name,
    BigDecimal price,
    Integer reorderPoint,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static ProductDto from(Product product) {
    return new ProductDto(
        product.getId(),
        product.getSku(),
        product.getName(),
        product.getPrice(),
        product.getReorderPoint(),
        product.isActive(),
        product.getCreatedAt(),
        product.getUpdatedAt());
  }
}
