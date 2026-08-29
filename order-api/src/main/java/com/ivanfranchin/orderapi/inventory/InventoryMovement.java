package com.ivanfranchin.orderapi.inventory;

import com.ivanfranchin.orderapi.config.TimePrecision;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.validation.BusinessText;
import com.ivanfranchin.orderapi.validation.ValidBusinessText;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

  public static final int REFERENCE_MAX_LENGTH = 128;
  public static final int REASON_MAX_LENGTH = 500;
  public static final int CREATED_BY_MAX_LENGTH = 64;
  static final int REFERENCE_COLUMN_LENGTH = REFERENCE_MAX_LENGTH * 2;
  static final int REASON_COLUMN_LENGTH = REASON_MAX_LENGTH * 2;

  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Inventory inventory;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Warehouse warehouse;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InventoryMovementType type;

  @Column(nullable = false)
  private long onHandDelta;

  @Column(nullable = false)
  private long reservedDelta;

  @Column(nullable = false)
  private long onHandBefore;

  @Column(nullable = false)
  private long onHandAfter;

  @Column(nullable = false)
  private long reservedBefore;

  @Column(nullable = false)
  private long reservedAfter;

  @ValidBusinessText(max = REFERENCE_MAX_LENGTH)
  @Column(length = REFERENCE_COLUMN_LENGTH)
  private String reference;

  @ValidBusinessText(max = REASON_MAX_LENGTH)
  @Column(length = REASON_COLUMN_LENGTH)
  private String reason;

  @Column(nullable = false, length = CREATED_BY_MAX_LENGTH)
  private String createdBy;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  static InventoryMovement create(
      Inventory inventory,
      InventoryMovementType type,
      long onHandDelta,
      long reservedDelta,
      long onHandBefore,
      long onHandAfter,
      long reservedBefore,
      long reservedAfter,
      String reference,
      String reason,
      String createdBy) {
    InventoryMovement movement = new InventoryMovement();
    movement.inventory = inventory;
    movement.product = inventory.getProduct();
    movement.warehouse = inventory.getWarehouse();
    movement.type = type;
    movement.onHandDelta = onHandDelta;
    movement.reservedDelta = reservedDelta;
    movement.onHandBefore = onHandBefore;
    movement.onHandAfter = onHandAfter;
    movement.reservedBefore = reservedBefore;
    movement.reservedAfter = reservedAfter;
    movement.reference = BusinessText.normalizeOptional(reference);
    movement.reason = BusinessText.normalizeOptional(reason);
    movement.createdBy = createdBy;
    return movement;
  }

  @PrePersist
  void onPrePersist() {
    normalizeAndValidateAuditText();
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    if (createdAt == null) {
      createdAt = TimePrecision.databasePrecision(Instant.now());
    }
  }

  @PreUpdate
  void onPreUpdate() {
    normalizeAndValidateAuditText();
  }

  private void normalizeAndValidateAuditText() {
    reference = normalizeAndValidate(reference, REFERENCE_MAX_LENGTH, false, "reference");
    reason =
        normalizeAndValidate(
            reason,
            REASON_MAX_LENGTH,
            type == InventoryMovementType.ADJUSTMENT_IN
                || type == InventoryMovementType.ADJUSTMENT_OUT,
            "reason");
  }

  private static String normalizeAndValidate(
      String value, int maxCodePoints, boolean required, String fieldName) {
    String normalized = BusinessText.normalizeOptional(value);
    if (!BusinessText.isValid(normalized, maxCodePoints, required)) {
      throw new IllegalStateException("Inventory movement %s is invalid".formatted(fieldName));
    }
    return normalized;
  }
}
