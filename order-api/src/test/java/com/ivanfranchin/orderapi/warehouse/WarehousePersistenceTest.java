package com.ivanfranchin.orderapi.warehouse;

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
@Import(WarehouseService.class)
class WarehousePersistenceTest {
  @Autowired WarehouseRepository repository;
  @Autowired WarehouseService service;
  @Autowired EntityManager entityManager;

  @Test
  void crudSoftDeleteAndUpdatedAt() {
    Warehouse created = service.createWarehouse(warehouse(" warehouse-1 "));
    assertThat(created.getId()).isNotBlank();
    assertThat(created.getWarehouseCode()).isEqualTo("WAREHOUSE-1");
    assertThat(created.isActive()).isTrue();

    created.setUpdatedAt(Instant.EPOCH);
    Warehouse changes = new Warehouse(" warehouse-2 ", "Updated", "Sydney");
    changes.setActive(true);
    Warehouse updated = service.updateWarehouse(created.getId(), changes);
    assertThat(updated.getWarehouseCode()).isEqualTo("WAREHOUSE-2");
    assertThat(updated.getUpdatedAt()).isAfter(Instant.EPOCH);

    service.deactivateWarehouse(created.getId());
    entityManager.flush();
    entityManager.clear();
    assertThat(repository.findById(created.getId()).orElseThrow().isActive()).isFalse();
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void caseAndWhitespaceVariantsAreDuplicates() {
    service.createWarehouse(warehouse("warehouse-1"));
    assertThatThrownBy(() -> service.createWarehouse(warehouse("\u2003WAREHOUSE-1\u00a0")))
        .isInstanceOf(DuplicateWarehouseCodeException.class);
  }

  @Test
  void databaseUniqueConstraintBackstopRejectsDuplicate() {
    repository.saveAndFlush(warehouse("warehouse-1"));
    assertThatThrownBy(() -> repository.saveAndFlush(warehouse("\u00a0WAREHOUSE-1\u2003")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databasePreservesCodeWith64SupplementaryCodePoints() {
    String code = "\ud83d\udce6".repeat(Warehouse.CODE_MAX_LENGTH);

    Warehouse saved = service.createWarehouse(warehouse(code));
    entityManager.clear();

    assertThat(repository.findById(saved.getId()).orElseThrow().getWarehouseCode()).isEqualTo(code);
  }

  @Test
  void directRepositoryCreateRejectsMoreThan64CodePoints() {
    Warehouse warehouse = warehouse("WAREHOUSE-1");
    ReflectionTestUtils.setField(
        warehouse, "warehouseCode", "\ud83d\udce6".repeat(Warehouse.CODE_MAX_LENGTH + 1));

    assertThatThrownBy(() -> repository.saveAndFlush(warehouse))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
            "Business code must contain at most 64 Unicode code points after normalization");
  }

  @Test
  void directRepositoryUpdateRejectsMoreThan64CodePoints() {
    Warehouse warehouse = repository.saveAndFlush(warehouse("WAREHOUSE-1"));
    ReflectionTestUtils.setField(
        warehouse, "warehouseCode", "\ud83d\udce6".repeat(Warehouse.CODE_MAX_LENGTH + 1));

    assertThatThrownBy(entityManager::flush)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("64 Unicode code points");
  }

  @Test
  void unrelatedLengthViolationIsNotDuplicateCode() {
    Warehouse warehouse =
        new Warehouse("warehouse-1", "Warehouse", "x".repeat(Warehouse.LOCATION_MAX_LENGTH + 1));
    assertThatThrownBy(() -> service.createWarehouse(warehouse))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateWarehouseCodeException.class);
  }

  private Warehouse warehouse(String code) {
    return new Warehouse(code, "Warehouse", "Melbourne");
  }
}
