package com.ivanfranchin.orderapi.supplier;

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
    name = "suppliers",
    uniqueConstraints =
        @UniqueConstraint(name = Supplier.CODE_UNIQUE_CONSTRAINT, columnNames = "supplier_code"))
public class Supplier {

  public static final String CODE_UNIQUE_CONSTRAINT = "uk_suppliers_supplier_code";
  public static final int CODE_MAX_LENGTH = 64;
  public static final int CODE_STORAGE_LENGTH = CODE_MAX_LENGTH * 2;
  public static final int NAME_MAX_LENGTH = 200;
  public static final int EMAIL_MAX_LENGTH = 254;
  public static final int PHONE_MAX_LENGTH = 32;

  @Id private String id;

  @ValidBusinessCode(max = CODE_MAX_LENGTH)
  @Column(nullable = false, length = CODE_STORAGE_LENGTH)
  private String supplierCode;

  @Column(nullable = false, length = NAME_MAX_LENGTH)
  private String name;

  @Column(length = EMAIL_MAX_LENGTH)
  private String contactEmail;

  @Column(length = PHONE_MAX_LENGTH)
  private String phone;

  @Column(nullable = false)
  private Integer leadTimeDays;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Supplier(
      String supplierCode, String name, String contactEmail, String phone, Integer leadTimeDays) {
    setSupplierCode(supplierCode);
    this.name = name;
    this.contactEmail = contactEmail;
    this.phone = phone;
    this.leadTimeDays = leadTimeDays;
  }

  @PrePersist
  public void onPrePersist() {
    if (id == null) id = UUID.randomUUID().toString();
    supplierCode = BusinessCode.normalizeAndValidate(supplierCode, CODE_MAX_LENGTH);
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  public void onPreUpdate() {
    supplierCode = BusinessCode.normalizeAndValidate(supplierCode, CODE_MAX_LENGTH);
    updatedAt = Instant.now();
  }

  public void setSupplierCode(String supplierCode) {
    this.supplierCode = BusinessCode.normalizeAndValidate(supplierCode, CODE_MAX_LENGTH);
  }
}
