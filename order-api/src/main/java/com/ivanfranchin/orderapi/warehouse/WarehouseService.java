package com.ivanfranchin.orderapi.warehouse;

import com.ivanfranchin.orderapi.validation.BusinessCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class WarehouseService {
  private final WarehouseRepository warehouseRepository;

  @Transactional(readOnly = true)
  public List<Warehouse> getWarehouses() {
    return warehouseRepository.findAllByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public Warehouse getWarehouse(String id) {
    return warehouseRepository
        .findById(id)
        .orElseThrow(
            () -> new WarehouseNotFoundException("Warehouse with id %s not found".formatted(id)));
  }

  @Transactional
  public Warehouse createWarehouse(Warehouse warehouse) {
    warehouse.setWarehouseCode(
        BusinessCode.normalizeAndValidate(warehouse.getWarehouseCode(), Warehouse.CODE_MAX_LENGTH));
    if (warehouseRepository.existsByWarehouseCode(warehouse.getWarehouseCode()))
      throw duplicate(warehouse.getWarehouseCode());
    return save(warehouse);
  }

  @Transactional
  public Warehouse updateWarehouse(String id, Warehouse changes) {
    Warehouse warehouse = getWarehouse(id);
    String code =
        BusinessCode.normalizeAndValidate(changes.getWarehouseCode(), Warehouse.CODE_MAX_LENGTH);
    if (warehouseRepository.existsByWarehouseCodeAndIdNot(code, id)) throw duplicate(code);
    warehouse.setWarehouseCode(code);
    warehouse.setName(changes.getName());
    warehouse.setLocation(changes.getLocation());
    warehouse.setActive(changes.isActive());
    return save(warehouse);
  }

  @Transactional
  public Warehouse deactivateWarehouse(String id) {
    Warehouse warehouse = getWarehouse(id);
    warehouse.setActive(false);
    return warehouseRepository.save(warehouse);
  }

  private Warehouse save(Warehouse warehouse) {
    try {
      return warehouseRepository.saveAndFlush(warehouse);
    } catch (DataIntegrityViolationException exception) {
      if (isCodeConstraint(exception)) throw duplicate(warehouse.getWarehouseCode());
      throw exception;
    }
  }

  private boolean isCodeConstraint(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && Warehouse.CODE_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName()))
        return true;
    }
    return false;
  }

  private DuplicateWarehouseCodeException duplicate(String code) {
    return new DuplicateWarehouseCodeException(
        "Warehouse with code %s already exists".formatted(code));
  }
}
