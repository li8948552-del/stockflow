package com.ivanfranchin.orderapi.inventory;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(
    name = "inventories",
    uniqueConstraints =
        @UniqueConstraint(
            name = Inventory.PRODUCT_WAREHOUSE_UNIQUE_CONSTRAINT,
            columnNames = {"product_id", "warehouse_id"}),
    check =
        @CheckConstraint(
            name = "ck_inventories_quantities",
            constraint = "on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand"))
public class Inventory {

  public static final String PRODUCT_WAREHOUSE_UNIQUE_CONSTRAINT =
      "uk_inventories_product_warehouse";

  @Id private String id;

  @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Product product;

  @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Warehouse warehouse;

  @NotNull @PositiveOrZero @Column(nullable = false)
  private Long onHand;

  @NotNull @PositiveOrZero @Column(nullable = false)
  private Long reserved = 0L;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Inventory(Product product, Warehouse warehouse, long onHand) {
    this.product = product;
    this.warehouse = warehouse;
    setOnHand(onHand);
    this.reserved = 0L;
  }

  public long getAvailable() {
    return Math.subtractExact(onHand, reserved);
  }

  public void setOnHand(long onHand) {
    if (onHand < 0) {
      throw new InvalidInventoryQuantityException("On-hand quantity must not be negative");
    }
    if (reserved != null && onHand < reserved) {
      throw new InsufficientInventoryException(
          "On-hand quantity must not be lower than reserved quantity");
    }
    this.onHand = onHand;
  }

  public void setReserved(long reserved) {
    if (reserved < 0) {
      throw new InvalidInventoryQuantityException("Reserved quantity must not be negative");
    }
    if (onHand != null && reserved > onHand) {
      throw new InsufficientInventoryException(
          "Reserved quantity must not exceed on-hand quantity");
    }
    this.reserved = reserved;
  }

  @AssertTrue(message = "reserved must not exceed onHand") public boolean isQuantityStateValid() {
    return onHand != null && reserved != null && onHand >= 0 && reserved >= 0 && reserved <= onHand;
  }

  @PrePersist
  void onPrePersist() {
    validateState();
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onPreUpdate() {
    validateState();
    updatedAt = Instant.now();
  }

  private void validateState() {
    if (product == null || warehouse == null) {
      throw new IllegalStateException("Inventory product and warehouse are required");
    }
    if (!isQuantityStateValid()) {
      throw new IllegalStateException("Inventory quantities are inconsistent");
    }
  }
}
