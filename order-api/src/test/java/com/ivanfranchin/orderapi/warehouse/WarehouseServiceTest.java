package com.ivanfranchin.orderapi.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(WarehouseService.class)
class WarehouseServiceTest {
  @MockitoBean WarehouseRepository repository;
  @Autowired WarehouseService service;

  @Test
  void create_normalizesCodeBeforeDuplicateCheck() {
    Warehouse warehouse = warehouse("\u2003warehouse-1\u00a0");
    when(repository.existsByWarehouseCode("WAREHOUSE-1")).thenReturn(true);
    assertThatThrownBy(() -> service.createWarehouse(warehouse))
        .isInstanceOf(DuplicateWarehouseCodeException.class);
    assertThat(warehouse.getWarehouseCode()).isEqualTo("WAREHOUSE-1");
  }

  @Test
  void createAndUpdateRejectMoreThan64CodePointsBeforePersistence() {
    String invalidCode = "\ud83d\udce6".repeat(Warehouse.CODE_MAX_LENGTH + 1);
    Warehouse invalidWarehouse = mock(Warehouse.class);
    when(invalidWarehouse.getWarehouseCode()).thenReturn(invalidCode);

    assertThatThrownBy(() -> service.createWarehouse(invalidWarehouse))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);

    Warehouse existing = warehouse("WAREHOUSE-1");
    Warehouse invalidChanges = mock(Warehouse.class);
    when(invalidChanges.getWarehouseCode()).thenReturn(invalidCode);
    when(repository.findById("id")).thenReturn(Optional.of(existing));
    assertThatThrownBy(() -> service.updateWarehouse("id", invalidChanges))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void update_normalizesCodeAndUpdatesFields() {
    Warehouse existing = warehouse("OLD");
    when(repository.findById("id")).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    Warehouse changes = new Warehouse("\u00a0new code\u2003", "New", "Sydney");
    changes.setActive(false);
    Warehouse result = service.updateWarehouse("id", changes);
    assertThat(result.getWarehouseCode()).isEqualTo("NEW CODE");
    assertThat(result.getLocation()).isEqualTo("Sydney");
    assertThat(result.isActive()).isFalse();
  }

  @Test
  void uniqueConstraintRace_isMappedToDuplicateCode() {
    Warehouse warehouse = warehouse("WAREHOUSE-1");
    ConstraintViolationException cause =
        new ConstraintViolationException(
            "duplicate", new SQLException("duplicate"), Warehouse.CODE_UNIQUE_CONSTRAINT);
    when(repository.saveAndFlush(warehouse))
        .thenThrow(new DataIntegrityViolationException("duplicate", cause));
    assertThatThrownBy(() -> service.createWarehouse(warehouse))
        .isInstanceOf(DuplicateWarehouseCodeException.class);
  }

  @Test
  void unrelatedIntegrityFailure_isPreserved() {
    Warehouse warehouse = warehouse("WAREHOUSE-1");
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException(
            "other",
            new ConstraintViolationException("other", new SQLException(), "other_constraint"));
    when(repository.saveAndFlush(warehouse)).thenThrow(failure);
    assertThatThrownBy(() -> service.createWarehouse(warehouse)).isSameAs(failure);
  }

  @Test
  void missingWarehouse_throwsNotFound() {
    when(repository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getWarehouse("missing"))
        .isInstanceOf(WarehouseNotFoundException.class);
  }

  private Warehouse warehouse(String code) {
    return new Warehouse(code, "Warehouse", "Melbourne");
  }
}
