package com.ivanfranchin.orderapi.supplier;

import com.ivanfranchin.orderapi.validation.BusinessCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SupplierService {
  private final SupplierRepository supplierRepository;

  @Transactional(readOnly = true)
  public List<Supplier> getSuppliers() {
    return supplierRepository.findAllByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public Supplier getSupplier(String id) {
    return supplierRepository
        .findById(id)
        .orElseThrow(
            () -> new SupplierNotFoundException("Supplier with id %s not found".formatted(id)));
  }

  @Transactional
  public Supplier createSupplier(Supplier supplier) {
    supplier.setSupplierCode(
        BusinessCode.normalizeAndValidate(supplier.getSupplierCode(), Supplier.CODE_MAX_LENGTH));
    if (supplierRepository.existsBySupplierCode(supplier.getSupplierCode()))
      throw duplicate(supplier.getSupplierCode());
    return save(supplier);
  }

  @Transactional
  public Supplier updateSupplier(String id, Supplier changes) {
    Supplier supplier = getSupplier(id);
    String code =
        BusinessCode.normalizeAndValidate(changes.getSupplierCode(), Supplier.CODE_MAX_LENGTH);
    if (supplierRepository.existsBySupplierCodeAndIdNot(code, id)) throw duplicate(code);
    supplier.setSupplierCode(code);
    supplier.setName(changes.getName());
    supplier.setContactEmail(changes.getContactEmail());
    supplier.setPhone(changes.getPhone());
    supplier.setLeadTimeDays(changes.getLeadTimeDays());
    supplier.setActive(changes.isActive());
    return save(supplier);
  }

  @Transactional
  public Supplier deactivateSupplier(String id) {
    Supplier supplier = getSupplier(id);
    supplier.setActive(false);
    return supplierRepository.save(supplier);
  }

  private Supplier save(Supplier supplier) {
    try {
      return supplierRepository.saveAndFlush(supplier);
    } catch (DataIntegrityViolationException exception) {
      if (isCodeConstraint(exception)) throw duplicate(supplier.getSupplierCode());
      throw exception;
    }
  }

  private boolean isCodeConstraint(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && Supplier.CODE_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName()))
        return true;
    }
    return false;
  }

  private DuplicateSupplierCodeException duplicate(String code) {
    return new DuplicateSupplierCodeException(
        "Supplier with code %s already exists".formatted(code));
  }
}
