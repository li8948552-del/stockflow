package com.ivanfranchin.orderapi.warehouse;

import com.ivanfranchin.orderapi.validation.BusinessCode;
import com.ivanfranchin.orderapi.validation.ValidBusinessCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(
    name = "warehouses",
    uniqueConstraints =
        @UniqueConstraint(name = Warehouse.CODE_UNIQUE_CONSTRAINT, columnNames = "warehouse_code"))
public class Warehouse {
  public static final String CODE_UNIQUE_CONSTRAINT = "uk_warehouses_warehouse_code";
  public static final int CODE_MAX_LENGTH = 64;
  public static final int CODE_STORAGE_LENGTH = CODE_MAX_LENGTH * 2;
  public static final int NAME_MAX_LENGTH = 200;
  public static final int LOCATION_MAX_LENGTH = 255;

  @Id private String id;

  @ValidBusinessCode(max = CODE_MAX_LENGTH)
  @Column(nullable = false, length = CODE_STORAGE_LENGTH)
  private String warehouseCode;

  @Column(nullable = false, length = NAME_MAX_LENGTH)
  private String name;

  @Column(nullable = false, length = LOCATION_MAX_LENGTH)
  private String location;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Warehouse(String warehouseCode, String name, String location) {
    setWarehouseCode(warehouseCode);
    this.name = name;
    this.location = location;
  }

  @PrePersist
  public void onPrePersist() {
    if (id == null) id = UUID.randomUUID().toString();
    warehouseCode = BusinessCode.normalizeAndValidate(warehouseCode, CODE_MAX_LENGTH);
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  public void onPreUpdate() {
    warehouseCode = BusinessCode.normalizeAndValidate(warehouseCode, CODE_MAX_LENGTH);
    updatedAt = Instant.now();
  }

  public void setWarehouseCode(String warehouseCode) {
    this.warehouseCode = BusinessCode.normalizeAndValidate(warehouseCode, CODE_MAX_LENGTH);
  }
}
