package com.ivanfranchin.orderapi.rest.dto;

import static com.ivanfranchin.orderapi.supplier.Supplier.CODE_MAX_LENGTH;
import static com.ivanfranchin.orderapi.supplier.Supplier.EMAIL_MAX_LENGTH;
import static com.ivanfranchin.orderapi.supplier.Supplier.NAME_MAX_LENGTH;
import static com.ivanfranchin.orderapi.supplier.Supplier.PHONE_MAX_LENGTH;

import com.ivanfranchin.orderapi.supplier.Supplier;
import com.ivanfranchin.orderapi.validation.ValidBusinessCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
    @ValidBusinessCode(max = CODE_MAX_LENGTH) String supplierCode,
    @NotBlank @Size(max = NAME_MAX_LENGTH) String name,
    @Email @Size(max = EMAIL_MAX_LENGTH) String contactEmail,
    @Size(max = PHONE_MAX_LENGTH) String phone,
    @NotNull @Min(0) @Max(3650) Integer leadTimeDays,
    @NotNull Boolean active) {
  public Supplier toDomain() {
    Supplier supplier = new Supplier(supplierCode, name, contactEmail, phone, leadTimeDays);
    supplier.setActive(active);
    return supplier;
  }
}
