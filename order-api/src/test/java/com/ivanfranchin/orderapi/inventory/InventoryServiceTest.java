package com.ivanfranchin.orderapi.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductNotFoundException;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseNotFoundException;
import com.ivanfranchin.orderapi.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@Import(InventoryService.class)
class InventoryServiceTest {

  @MockitoBean private InventoryRepository inventoryRepository;
  @MockitoBean private InventoryMovementRepository movementRepository;
  @MockitoBean private ProductRepository productRepository;
  @MockitoBean private WarehouseRepository warehouseRepository;
  @Autowired private InventoryService inventoryService;

  private Product product;
  private Warehouse warehouse;

  @BeforeEach
  void setUp() {
    product = new Product("SKU-1", "Keyboard", BigDecimal.ONE, 10);
    warehouse = new Warehouse("WH-1", "Main", "Sydney");
    ReflectionTestUtils.setField(product, "id", "product-id");
    ReflectionTestUtils.setField(warehouse, "id", "warehouse-id");
  }

  @Test
  void firstReceiptCreatesInventoryAndInitialMovement() {
    activeReferences();
    when(inventoryRepository.findByProductIdAndWarehouseId("product-id", "warehouse-id"))
        .thenReturn(Optional.empty());
    when(inventoryRepository.saveAndFlush(any(Inventory.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0)));

    Inventory result = inventoryService.receive("product-id", "warehouse-id", 100, "PO-1", "admin");

    assertThat(result.getOnHand()).isEqualTo(100);
    assertThat(result.getReserved()).isZero();
    InOrder lockAndLookupOrder =
        inOrder(warehouseRepository, productRepository, inventoryRepository);
    lockAndLookupOrder.verify(warehouseRepository).findByIdForUpdate("warehouse-id");
    lockAndLookupOrder.verify(productRepository).findByIdForUpdate("product-id");
    lockAndLookupOrder
        .verify(inventoryRepository)
        .findByProductIdAndWarehouseId("product-id", "warehouse-id");
    verify(movementRepository)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                movement ->
                    movement.getType() == InventoryMovementType.INITIAL_STOCK
                        && movement.getQuantityDelta() == 100
                        && movement.getOnHandBefore() == 0
                        && movement.getOnHandAfter() == 100));
  }

  @Test
  void subsequentReceiptAddsStockAndCreatesReceiptMovement() {
    activeReferences();
    Inventory inventory = inventory(80);
    when(inventoryRepository.findByProductIdAndWarehouseId("product-id", "warehouse-id"))
        .thenReturn(Optional.of(inventory));
    when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);

    Inventory result = inventoryService.receive("product-id", "warehouse-id", 20, null, "admin");

    assertThat(result.getOnHand()).isEqualTo(100);
    verify(movementRepository)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                movement ->
                    movement.getType() == InventoryMovementType.RECEIPT
                        && movement.getOnHandBefore() == 80
                        && movement.getOnHandAfter() == 100));
  }

  @Test
  void positiveAndNegativeAdjustmentsCreateMatchingMovementTypes() {
    Inventory inventory = inventory(100);
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));
    when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);

    inventoryService.adjust("inventory-id", 20, "Correction", null, "admin");
    inventoryService.adjust("inventory-id", -30, "Damage", "REF", "admin");

    assertThat(inventory.getOnHand()).isEqualTo(90);
    verify(movementRepository)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                movement -> movement.getType() == InventoryMovementType.ADJUSTMENT_IN));
    verify(movementRepository)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                movement -> movement.getType() == InventoryMovementType.ADJUSTMENT_OUT));
  }

  @Test
  void rejectsInvalidReceiptAndAdjustmentInputs() {
    assertThatThrownBy(
            () -> inventoryService.receive("product-id", "warehouse-id", 0, null, "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class);
    assertThatThrownBy(
            () -> inventoryService.receive("product-id", "warehouse-id", -1, null, "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class);
    assertThatThrownBy(() -> inventoryService.adjust("inventory-id", 0, "Reason", null, "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class);
    assertThatThrownBy(() -> inventoryService.adjust("inventory-id", 1, " ", null, "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class);
    verifyNoInteractions(movementRepository);
  }

  @Test
  void adjustmentCannotGoNegativeOrBelowReserved() {
    Inventory inventory = inventory(10);
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));
    assertThatThrownBy(() -> inventoryService.adjust("inventory-id", -11, "Damage", null, "admin"))
        .isInstanceOf(InsufficientInventoryException.class);

    inventory.setReserved(8);
    assertThatThrownBy(() -> inventoryService.adjust("inventory-id", -3, "Damage", null, "admin"))
        .isInstanceOf(InsufficientInventoryException.class)
        .hasMessageContaining("reserved");
    verify(inventoryRepository, never()).saveAndFlush(any());
    verifyNoInteractions(movementRepository);
  }

  @Test
  void missingReferencesProduceExistingNotFoundExceptions() {
    when(warehouseRepository.findByIdForUpdate("warehouse-id")).thenReturn(Optional.of(warehouse));
    when(productRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> inventoryService.receive("missing", "warehouse-id", 1, null, "admin"))
        .isInstanceOf(ProductNotFoundException.class);

    when(productRepository.findByIdForUpdate("product-id")).thenReturn(Optional.of(product));
    when(warehouseRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> inventoryService.receive("product-id", "missing", 1, null, "admin"))
        .isInstanceOf(WarehouseNotFoundException.class);

    when(inventoryRepository.findDetailedById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> inventoryService.getInventory("missing"))
        .isInstanceOf(InventoryNotFoundException.class);
  }

  @Test
  void rejectsInactiveProductAndWarehouse() {
    product.setActive(false);
    when(warehouseRepository.findByIdForUpdate("warehouse-id")).thenReturn(Optional.of(warehouse));
    when(productRepository.findByIdForUpdate("product-id")).thenReturn(Optional.of(product));
    assertThatThrownBy(
            () -> inventoryService.receive("product-id", "warehouse-id", 1, null, "admin"))
        .isInstanceOf(InactiveInventoryReferenceException.class)
        .hasMessageContaining("Product");

    product.setActive(true);
    warehouse.setActive(false);
    when(warehouseRepository.findByIdForUpdate("warehouse-id")).thenReturn(Optional.of(warehouse));
    assertThatThrownBy(
            () -> inventoryService.receive("product-id", "warehouse-id", 1, null, "admin"))
        .isInstanceOf(InactiveInventoryReferenceException.class)
        .hasMessageContaining("Warehouse");
  }

  @Test
  void detectsOverflowForReceiptAndAdjustment() {
    activeReferences();
    Inventory inventory = inventory(Long.MAX_VALUE);
    when(inventoryRepository.findByProductIdAndWarehouseId("product-id", "warehouse-id"))
        .thenReturn(Optional.of(inventory));
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));

    assertThatThrownBy(
            () -> inventoryService.receive("product-id", "warehouse-id", 1, null, "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class)
        .hasMessageContaining("range");
    assertThatThrownBy(
            () -> inventoryService.adjust("inventory-id", 1, "Correction", null, "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class)
        .hasMessageContaining("range");
  }

  @Test
  void mapsOnlyNamedUniqueConstraintAndPreservesOtherIntegrityFailures() {
    activeReferences();
    when(inventoryRepository.findByProductIdAndWarehouseId("product-id", "warehouse-id"))
        .thenReturn(Optional.empty());
    DataIntegrityViolationException uniqueFailure =
        integrityFailure(Inventory.PRODUCT_WAREHOUSE_UNIQUE_CONSTRAINT);
    when(inventoryRepository.saveAndFlush(any())).thenThrow(uniqueFailure);

    assertThatThrownBy(
            () -> inventoryService.receive("product-id", "warehouse-id", 1, null, "admin"))
        .isInstanceOf(InventoryConflictException.class);

    reset(inventoryRepository);
    when(inventoryRepository.findByProductIdAndWarehouseId("product-id", "warehouse-id"))
        .thenReturn(Optional.empty());
    DataIntegrityViolationException unrelated = integrityFailure("ck_inventories_quantities");
    when(inventoryRepository.saveAndFlush(any())).thenThrow(unrelated);
    assertThatThrownBy(
            () -> inventoryService.receive("product-id", "warehouse-id", 1, null, "admin"))
        .isSameAs(unrelated);
  }

  @Test
  void mapsOptimisticLockFailureToConflict() {
    Inventory inventory = inventory(10);
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));
    when(inventoryRepository.saveAndFlush(inventory))
        .thenThrow(new OptimisticLockingFailureException("stale"));

    assertThatThrownBy(
            () -> inventoryService.adjust("inventory-id", 1, "Correction", null, "admin"))
        .isInstanceOf(InventoryOptimisticLockException.class);
    verifyNoInteractions(movementRepository);
  }

  @Test
  void movementQueryValidatesInventoryAndPreservesRepositoryOrder() {
    Inventory inventory = inventory(10);
    InventoryMovement movement = mock(InventoryMovement.class);
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));
    when(movementRepository.findDetailedByInventoryIdOrderByCreatedAtDescIdDesc("inventory-id"))
        .thenReturn(List.of(movement));

    assertThat(inventoryService.getMovements("inventory-id")).containsExactly(movement);
  }

  @Test
  void normalizesUnicodeAuditBoundariesAndPreservesContent() {
    activeReferences();
    Inventory inventory = inventory(10);
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));
    when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);

    inventoryService.adjust(
        "inventory-id",
        1,
        "\u00a0\u2003Cycle\u2003 Count\u00a0\u2003",
        "\u2003 Ref\u00a0 Value \u00a0",
        "admin");

    ArgumentCaptor<InventoryMovement> movementCaptor =
        ArgumentCaptor.forClass(InventoryMovement.class);
    verify(movementRepository).saveAndFlush(movementCaptor.capture());
    assertThat(movementCaptor.getValue().getReason()).isEqualTo("Cycle\u2003 Count");
    assertThat(movementCaptor.getValue().getReference()).isEqualTo("Ref\u00a0 Value");
  }

  @Test
  void rejectsUnicodeOnlyReasonAndNormalizesUnicodeOnlyReferenceToNull() {
    Inventory inventory = inventory(10);
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));

    assertThatThrownBy(
            () -> inventoryService.adjust("inventory-id", 1, "\u00a0\u2003", null, "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class)
        .hasMessageContaining("blank");

    activeReferences();
    when(inventoryRepository.findByProductIdAndWarehouseId("product-id", "warehouse-id"))
        .thenReturn(Optional.empty());
    when(inventoryRepository.saveAndFlush(any(Inventory.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0)));
    inventoryService.receive("product-id", "warehouse-id", 1, "\u00a0\u2003", "admin");

    ArgumentCaptor<InventoryMovement> movementCaptor =
        ArgumentCaptor.forClass(InventoryMovement.class);
    verify(movementRepository).saveAndFlush(movementCaptor.capture());
    assertThat(movementCaptor.getValue().getReference()).isNull();
  }

  @Test
  void auditLengthUsesNormalizedUnicodeCodePoints() {
    activeReferences();
    when(inventoryRepository.findByProductIdAndWarehouseId("product-id", "warehouse-id"))
        .thenReturn(Optional.empty());
    when(inventoryRepository.saveAndFlush(any(Inventory.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0)));

    String validReference = "📦".repeat(InventoryMovement.REFERENCE_MAX_LENGTH);
    inventoryService.receive(
        "product-id", "warehouse-id", 1, "\u00a0" + validReference + "\u2003", "admin");
    assertThatThrownBy(
            () ->
                inventoryService.receive(
                    "product-id",
                    "warehouse-id",
                    1,
                    "📦".repeat(InventoryMovement.REFERENCE_MAX_LENGTH + 1),
                    "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class)
        .hasMessageContaining("too long");

    Inventory inventory = inventory(10);
    when(inventoryRepository.findDetailedById("inventory-id")).thenReturn(Optional.of(inventory));
    when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);
    inventoryService.adjust(
        "inventory-id", 1, "📦".repeat(InventoryMovement.REASON_MAX_LENGTH), null, "admin");
    assertThatThrownBy(
            () ->
                inventoryService.adjust(
                    "inventory-id",
                    1,
                    "📦".repeat(InventoryMovement.REASON_MAX_LENGTH + 1),
                    null,
                    "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class)
        .hasMessageContaining("too long");
  }

  private void activeReferences() {
    product.setActive(true);
    warehouse.setActive(true);
    when(productRepository.findByIdForUpdate("product-id")).thenReturn(Optional.of(product));
    when(warehouseRepository.findByIdForUpdate("warehouse-id")).thenReturn(Optional.of(warehouse));
  }

  private Inventory inventory(long onHand) {
    Inventory inventory = new Inventory(product, warehouse, onHand);
    return withId(inventory);
  }

  private Inventory withId(Inventory inventory) {
    ReflectionTestUtils.setField(inventory, "id", "inventory-id");
    return inventory;
  }

  private DataIntegrityViolationException integrityFailure(String constraintName) {
    return new DataIntegrityViolationException(
        "constraint",
        new ConstraintViolationException(
            "constraint", new SQLException("constraint"), constraintName));
  }
}
