package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.product.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonSerialize;

public record ProductDto(
    String id,
    String sku,
    String name,
    @Schema(type = "string", format = "decimal", example = "89.95")
        @JsonSerialize(using = PlainBigDecimalSerializer.class)
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
