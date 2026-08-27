package com.ivanfranchin.orderapi.rest.dto;

import static com.ivanfranchin.orderapi.product.Product.NAME_MAX_LENGTH;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.validation.ValidProductSku;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
    @Schema(example = "SKU-001") @ValidProductSku String sku,
    @Schema(example = "Wireless Keyboard") @NotBlank @Size(max = NAME_MAX_LENGTH) String name,
    @Schema(example = "89.95") @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
    @Schema(example = "10") @NotNull @PositiveOrZero Integer reorderPoint) {

  public Product toDomain() {
    return new Product(sku, name, price, reorderPoint);
  }
}
