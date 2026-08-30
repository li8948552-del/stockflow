package com.ivanfranchin.orderapi.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.supplier.Supplier;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PurchaseOrderDomainTest {
  @Test
  void createsDraftWithStableLinesAndTotal() {
    Supplier supplier = new Supplier("SUP-1", "Supplier", null, null, 2);
    Warehouse warehouse = new Warehouse("WH-1", "Main", "Sydney");
    Product product = new Product("SKU-1", "Widget", new BigDecimal("3.25"), 1);
    PurchaseOrder order = new PurchaseOrder("po-1", supplier, warehouse, null);
    order.addItem(product, 4, new BigDecimal("2.50"), 1);
    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
    assertThat(order.getItems())
        .singleElement()
        .satisfies(i -> assertThat(i.getLineNumber()).isEqualTo(1));
    assertThat(order.getTotalAmount()).isEqualByComparingTo("10.00");
  }

  @Test
  void submitAndCancelAreIdempotent() {
    PurchaseOrder order = order();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    order.submit(now);
    order.submit(now.plusSeconds(1));
    assertThat(order.getSubmittedAt()).isEqualTo(now);
    order.cancel(now.plusSeconds(2));
    order.cancel(now.plusSeconds(3));
    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
  }

  @Test
  void cannotReceiveMoreThanOrdered() {
    PurchaseOrderItem item = orderItem();
    item.receive(2);
    assertThatThrownBy(() -> item.receive(1)).isInstanceOf(OverReceiptException.class);
    assertThat(item.getReceivedQuantity()).isEqualTo(2);
  }

  private PurchaseOrder order() {
    PurchaseOrder order =
        new PurchaseOrder(
            "po-1",
            new Supplier("SUP-1", "Supplier", null, null, 2),
            new Warehouse("WH-1", "Main", "Sydney"),
            null);
    order.addItem(
        new Product("SKU-1", "Widget", new BigDecimal("3.25"), 1), 2, new BigDecimal("2.50"), 1);
    return order;
  }

  private PurchaseOrderItem orderItem() {
    return order().getItems().get(0);
  }
}
