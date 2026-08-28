package com.ivanfranchin.orderapi.supplier;

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
@Import(SupplierService.class)
class SupplierServiceTest {
  @MockitoBean SupplierRepository repository;
  @Autowired SupplierService service;

  @Test
  void create_normalizesCodeBeforeDuplicateCheck() {
    Supplier supplier = supplier("\u2003supplier-1\u00a0");
    when(repository.existsBySupplierCode("SUPPLIER-1")).thenReturn(true);
    assertThatThrownBy(() -> service.createSupplier(supplier))
        .isInstanceOf(DuplicateSupplierCodeException.class);
    assertThat(supplier.getSupplierCode()).isEqualTo("SUPPLIER-1");
  }

  @Test
  void createAndUpdateRejectMoreThan64CodePointsBeforePersistence() {
    String invalidCode = "\ud83d\udce6".repeat(Supplier.CODE_MAX_LENGTH + 1);
    Supplier invalidSupplier = mock(Supplier.class);
    when(invalidSupplier.getSupplierCode()).thenReturn(invalidCode);

    assertThatThrownBy(() -> service.createSupplier(invalidSupplier))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);

    Supplier existing = supplier("SUPPLIER-1");
    Supplier invalidChanges = mock(Supplier.class);
    when(invalidChanges.getSupplierCode()).thenReturn(invalidCode);
    when(repository.findById("id")).thenReturn(Optional.of(existing));
    assertThatThrownBy(() -> service.updateSupplier("id", invalidChanges))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void update_normalizesCodeAndUpdatesFields() {
    Supplier existing = supplier("OLD");
    when(repository.findById("id")).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);
    Supplier changes = new Supplier("\u00a0new code\u2003", "New", "new@example.com", "2", 5);
    changes.setActive(false);
    Supplier result = service.updateSupplier("id", changes);
    assertThat(result.getSupplierCode()).isEqualTo("NEW CODE");
    assertThat(result.getName()).isEqualTo("New");
    assertThat(result.isActive()).isFalse();
  }

  @Test
  void uniqueConstraintRace_isMappedToDuplicateCode() {
    Supplier supplier = supplier("SUPPLIER-1");
    ConstraintViolationException cause =
        new ConstraintViolationException(
            "duplicate", new SQLException("duplicate"), Supplier.CODE_UNIQUE_CONSTRAINT);
    when(repository.saveAndFlush(supplier))
        .thenThrow(new DataIntegrityViolationException("duplicate", cause));
    assertThatThrownBy(() -> service.createSupplier(supplier))
        .isInstanceOf(DuplicateSupplierCodeException.class);
  }

  @Test
  void unrelatedIntegrityFailure_isPreserved() {
    Supplier supplier = supplier("SUPPLIER-1");
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException(
            "other",
            new ConstraintViolationException("other", new SQLException(), "other_constraint"));
    when(repository.saveAndFlush(supplier)).thenThrow(failure);
    assertThatThrownBy(() -> service.createSupplier(supplier)).isSameAs(failure);
  }

  @Test
  void missingSupplier_throwsNotFound() {
    when(repository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getSupplier("missing"))
        .isInstanceOf(SupplierNotFoundException.class);
  }

  private Supplier supplier(String code) {
    return new Supplier(code, "Supplier", "a@example.com", "1", 3);
  }
}
