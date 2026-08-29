package com.ivanfranchin.orderapi.order;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order {
  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "warehouse_id", nullable = false)
  private Warehouse warehouse;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private OrderStatus status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @OrderBy("lineNumber ASC")
  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> items = new ArrayList<>();

  public Order(String id, User user, Warehouse warehouse, Instant expiresAt) {
    this.id = id;
    this.user = user;
    this.warehouse = warehouse;
    this.status = OrderStatus.RESERVED;
    this.totalAmount = BigDecimal.ZERO.setScale(2);
    this.expiresAt = expiresAt;
  }

  public List<OrderItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  public void addItem(Product product, long quantity, int lineNumber) {
    OrderItem item = new OrderItem(this, product, quantity, product.getPrice(), lineNumber);
    items.add(item);
    totalAmount = Money.add(totalAmount, item.getLineTotal());
  }

  public void cancel() {
    if (status == OrderStatus.CANCELLED) return;
    if (status != OrderStatus.RESERVED) {
      throw new InvalidOrderStatusException(
          "Order in status %s cannot be cancelled".formatted(status));
    }
    status = OrderStatus.CANCELLED;
  }

  public boolean isOwnedBy(String username) {
    return user != null && user.getUsername().equals(username);
  }

  @PrePersist
  void onPrePersist() {
    validateState();
    if (id == null) id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onPreUpdate() {
    validateState();
    updatedAt = Instant.now();
  }

  private void validateState() {
    if (user == null || warehouse == null || status == null || expiresAt == null) {
      throw new IllegalStateException("Order references, status and expiry are required");
    }
    if (items == null || items.isEmpty()) {
      throw new EmptyOrderException("Order must contain at least one item");
    }
    Money.requireDatabaseValue(totalAmount, "totalAmount");
  }
}
