package com.ivanfranchin.orderapi.procurement;

import com.ivanfranchin.orderapi.order.Money;
import com.ivanfranchin.orderapi.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(
    name = "purchase_order_items",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_purchase_items_order_product",
          columnNames = {"purchase_order_id", "product_id"}),
      @UniqueConstraint(
          name = "uk_purchase_items_order_line",
          columnNames = {"purchase_order_id", "line_number"})
    })
public class PurchaseOrderItem {
  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PurchaseOrder purchaseOrder;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @NotNull @Positive @Column(nullable = false)
  private long orderedQuantity;

  @Column(nullable = false)
  private long receivedQuantity;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal unitCost;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal lineTotal;

  @NotNull @Positive @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  PurchaseOrderItem(PurchaseOrder po, Product p, long q, BigDecimal cost, int line) {
    id = UUID.randomUUID().toString();
    purchaseOrder = po;
    product = p;
    orderedQuantity = q;
    receivedQuantity = 0;
    unitCost = cost;
    lineTotal = Money.multiply(cost, q, "lineTotal");
    lineNumber = line;
  }

  public long getRemainingQuantity() {
    return orderedQuantity - receivedQuantity;
  }

  public void receive(long quantity) {
    if (quantity <= 0 || quantity > getRemainingQuantity())
      throw new OverReceiptException("Receipt exceeds ordered quantity");
    receivedQuantity = Math.addExact(receivedQuantity, quantity);
  }
}
