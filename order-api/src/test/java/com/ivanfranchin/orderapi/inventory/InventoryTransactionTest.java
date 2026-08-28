package com.ivanfranchin.orderapi.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(InventoryService.class)
class InventoryTransactionTest {

  @Autowired private InventoryService inventoryService;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private WarehouseRepository warehouseRepository;
  @MockitoSpyBean private InventoryMovementRepository movementRepository;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void movementFailureRollsBackInventoryCreation() {
    Product product =
        productRepository.saveAndFlush(new Product("SKU-ATOMIC", "Keyboard", BigDecimal.ONE, 10));
    Warehouse warehouse =
        warehouseRepository.saveAndFlush(new Warehouse("WH-ATOMIC", "Main", "Sydney"));
    doThrow(new DataAccessResourceFailureException("movement unavailable"))
        .when(movementRepository)
        .saveAndFlush(any(InventoryMovement.class));

    assertThatThrownBy(
            () -> inventoryService.receive(product.getId(), warehouse.getId(), 10, null, "admin"))
        .isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(
            inventoryRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()))
        .isEmpty();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void movementFailureRollsBackExistingInventoryUpdate() {
    Product product =
        productRepository.saveAndFlush(
            new Product("SKU-ATOMIC-UPDATE", "Keyboard", BigDecimal.ONE, 10));
    Warehouse warehouse =
        warehouseRepository.saveAndFlush(new Warehouse("WH-ATOMIC-UPDATE", "Main", "Sydney"));
    Inventory inventory = inventoryRepository.saveAndFlush(new Inventory(product, warehouse, 10));
    doThrow(new DataAccessResourceFailureException("movement unavailable"))
        .when(movementRepository)
        .saveAndFlush(any(InventoryMovement.class));

    assertThatThrownBy(
            () ->
                inventoryService.receive(product.getId(), warehouse.getId(), 5, "PO-FAIL", "admin"))
        .isInstanceOf(DataAccessResourceFailureException.class);

    Inventory reloaded = inventoryRepository.findById(inventory.getId()).orElseThrow();
    assertThat(reloaded.getOnHand()).isEqualTo(10);
    assertThat(movementRepository.findAll()).isEmpty();
  }
}
