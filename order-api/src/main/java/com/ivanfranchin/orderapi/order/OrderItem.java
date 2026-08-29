package com.ivanfranchin.orderapi.order;

import com.ivanfranchin.orderapi.product.Product;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(
    name = "order_items",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_order_items_order_product",
          columnNames = {"order_id", "product_id"}),
      @UniqueConstraint(
          name = "uk_order_items_order_line_number",
          columnNames = {"order_id", "line_number"})
    },
    check =
        @CheckConstraint(
            name = "ck_order_items_line_number_positive",
            constraint = "line_number > 0"))
public class OrderItem {
  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @Column(nullable = false, updatable = false)
  private long quantity;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(nullable = false, precision = 19, scale = 2, updatable = false)
  private BigDecimal unitPrice;

  @Column(nullable = false, precision = 19, scale = 2, updatable = false)
  private BigDecimal lineTotal;

  OrderItem(Order order, Product product, long quantity, BigDecimal unitPrice, int lineNumber) {
    if (quantity <= 0)
      throw new InvalidOrderException("Order item quantity must be greater than zero");
    this.order = order;
    this.product = product;
    this.quantity = quantity;
    if (lineNumber <= 0)
      throw new InvalidOrderException("Order item line number must be greater than zero");
    this.lineNumber = lineNumber;
    this.unitPrice = Money.requireDatabaseValue(unitPrice, "unitPrice");
    this.lineTotal = Money.multiply(this.unitPrice, quantity, "lineTotal");
  }

  @PrePersist
  void onPrePersist() {
    if (id == null) id = UUID.randomUUID().toString();
    if (order == null || product == null || quantity <= 0 || lineNumber <= 0) {
      throw new IllegalStateException(
          "Order item references, quantity and line number must be valid");
    }
    unitPrice = Money.requireDatabaseValue(unitPrice, "unitPrice");
    lineTotal = Money.multiply(unitPrice, quantity, "lineTotal");
  }
}
