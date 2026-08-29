package com.ivanfranchin.orderapi.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryTest {

  @Test
  void calculatesAvailableWithoutPersistingIt() {
    Inventory inventory = inventory(100);
    inventory.setReserved(35);

    assertThat(inventory.getAvailable()).isEqualTo(65);
  }

  @Test
  void rejectsNegativeOnHandAndReserved() {
    assertThatThrownBy(() -> inventory(-1)).isInstanceOf(InvalidInventoryQuantityException.class);
    assertThatThrownBy(() -> inventory(1).setReserved(-1))
        .isInstanceOf(InvalidInventoryQuantityException.class);
  }

  @Test
  void rejectsReservedAboveOnHandAndOnHandBelowReserved() {
    Inventory inventory = inventory(10);
    assertThatThrownBy(() -> inventory.setReserved(11))
        .isInstanceOf(InsufficientInventoryException.class);

    inventory.setReserved(5);
    assertThatThrownBy(() -> inventory.setOnHand(4))
        .isInstanceOf(InsufficientInventoryException.class);
  }

  private Inventory inventory(long onHand) {
    Product product = new Product("SKU-1", "Keyboard", BigDecimal.ONE, 10);
    Warehouse warehouse = new Warehouse("WH-1", "Main", "Sydney");
    return new Inventory(product, warehouse, onHand);
  }
}
