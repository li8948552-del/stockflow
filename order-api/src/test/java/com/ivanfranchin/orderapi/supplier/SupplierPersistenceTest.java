package com.ivanfranchin.orderapi.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import(SupplierService.class)
class SupplierPersistenceTest {
  @Autowired SupplierRepository repository;
  @Autowired SupplierService service;
  @Autowired EntityManager entityManager;

  @Test
  void crudSoftDeleteAndUpdatedAt() {
    Supplier created = service.createSupplier(supplier(" supplier-1 "));
    assertThat(created.getId()).isNotBlank();
    assertThat(created.getSupplierCode()).isEqualTo("SUPPLIER-1");
    assertThat(created.isActive()).isTrue();

    created.setUpdatedAt(Instant.EPOCH);
    Supplier changes = new Supplier(" supplier-2 ", "Updated", null, null, 0);
    changes.setActive(true);
    Supplier updated = service.updateSupplier(created.getId(), changes);
    assertThat(updated.getSupplierCode()).isEqualTo("SUPPLIER-2");
    assertThat(updated.getUpdatedAt()).isAfter(Instant.EPOCH);

    service.deactivateSupplier(created.getId());
    entityManager.flush();
    entityManager.clear();
    assertThat(repository.findById(created.getId()).orElseThrow().isActive()).isFalse();
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void caseAndWhitespaceVariantsAreDuplicates() {
    service.createSupplier(supplier("supplier-1"));
    assertThatThrownBy(() -> service.createSupplier(supplier("\u2003SUPPLIER-1\u00a0")))
        .isInstanceOf(DuplicateSupplierCodeException.class);
  }

  @Test
  void databaseUniqueConstraintBackstopRejectsDuplicate() {
    repository.saveAndFlush(supplier("supplier-1"));
    assertThatThrownBy(() -> repository.saveAndFlush(supplier("\u00a0SUPPLIER-1\u2003")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databasePreservesCodeWith64SupplementaryCodePoints() {
    String code = "\ud83d\udce6".repeat(Supplier.CODE_MAX_LENGTH);

    Supplier saved = service.createSupplier(supplier(code));
    entityManager.clear();

    assertThat(repository.findById(saved.getId()).orElseThrow().getSupplierCode()).isEqualTo(code);
  }

  @Test
  void directRepositoryCreateRejectsMoreThan64CodePoints() {
    Supplier supplier = supplier("SUPPLIER-1");
    ReflectionTestUtils.setField(
        supplier, "supplierCode", "\ud83d\udce6".repeat(Supplier.CODE_MAX_LENGTH + 1));

    assertThatThrownBy(() -> repository.saveAndFlush(supplier))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
            "Business code must contain at most 64 Unicode code points after normalization");
  }

  @Test
  void directRepositoryUpdateRejectsMoreThan64CodePoints() {
    Supplier supplier = repository.saveAndFlush(supplier("SUPPLIER-1"));
    ReflectionTestUtils.setField(
        supplier, "supplierCode", "\ud83d\udce6".repeat(Supplier.CODE_MAX_LENGTH + 1));

    assertThatThrownBy(entityManager::flush)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("64 Unicode code points");
  }

  @Test
  void unrelatedLengthViolationIsNotDuplicateCode() {
    Supplier supplier =
        new Supplier("supplier-1", "x".repeat(Supplier.NAME_MAX_LENGTH + 1), null, null, 0);
    assertThatThrownBy(() -> service.createSupplier(supplier))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateSupplierCodeException.class);
  }

  private Supplier supplier(String code) {
    return new Supplier(code, "Supplier", "contact@example.com", "+61", 5);
  }
}
