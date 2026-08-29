package com.ivanfranchin.orderapi.product;

import com.ivanfranchin.orderapi.config.TimePrecision;
import com.ivanfranchin.orderapi.product.validation.ValidProductSku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(
    name = "products",
    uniqueConstraints =
        @UniqueConstraint(name = Product.SKU_UNIQUE_CONSTRAINT, columnNames = "sku"))
public class Product {

  public static final String SKU_UNIQUE_CONSTRAINT = "uk_products_sku";
  public static final int SKU_MAX_LENGTH = 64;
  public static final int SKU_STORAGE_LENGTH = SKU_MAX_LENGTH * 2;
  public static final int NAME_MAX_LENGTH = 255;

  @Id private String id;

  @ValidProductSku
  @Column(nullable = false, length = SKU_STORAGE_LENGTH)
  private String sku;

  @Column(nullable = false, length = NAME_MAX_LENGTH)
  private String name;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private Integer reorderPoint;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Product(String sku, String name, BigDecimal price, Integer reorderPoint) {
    setSku(sku);
    this.name = name;
    this.price = price;
    this.reorderPoint = reorderPoint;
  }

  @PrePersist
  public void onPrePersist() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    normalizeSku();
    Instant now = TimePrecision.databasePrecision(Instant.now());
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  public void onPreUpdate() {
    normalizeSku();
    updatedAt = TimePrecision.databasePrecision(Instant.now());
  }

  private void normalizeSku() {
    sku = ProductSku.normalizeAndValidate(sku);
  }

  public void setSku(String sku) {
    this.sku = ProductSku.normalizeAndValidate(sku);
  }
}
