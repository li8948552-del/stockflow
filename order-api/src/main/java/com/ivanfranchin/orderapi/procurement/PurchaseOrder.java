package com.ivanfranchin.orderapi.procurement;

import com.ivanfranchin.orderapi.config.TimePrecision;
import com.ivanfranchin.orderapi.order.Money;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.supplier.Supplier;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "supplier_id", nullable = false)
  private Supplier supplier;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "warehouse_id", nullable = false)
  private Warehouse warehouse;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private PurchaseOrderStatus status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  private LocalDate expectedDeliveryDate;
  private Instant submittedAt;
  private Instant cancelledAt;
  private Instant completedAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @OrderBy("lineNumber ASC")
  @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<PurchaseOrderItem> items = new ArrayList<>();

  public PurchaseOrder(
      String id, Supplier supplier, Warehouse warehouse, LocalDate expectedDeliveryDate) {
    this.id = id;
    this.supplier = supplier;
    this.warehouse = warehouse;
    this.expectedDeliveryDate = expectedDeliveryDate;
    this.status = PurchaseOrderStatus.DRAFT;
    this.totalAmount = BigDecimal.ZERO.setScale(2);
  }

  public void addItem(Product product, long quantity, BigDecimal unitCost, int lineNumber) {
    PurchaseOrderItem item = new PurchaseOrderItem(this, product, quantity, unitCost, lineNumber);
    items.add(item);
    totalAmount = Money.add(totalAmount, item.getLineTotal());
  }

  public void submit(Instant now) {
    if (status == PurchaseOrderStatus.SUBMITTED) return;
    if (status != PurchaseOrderStatus.DRAFT)
      throw new InvalidPurchaseOrderStateException("Only draft purchase orders can be submitted");
    status = PurchaseOrderStatus.SUBMITTED;
    submittedAt = now;
  }

  public void cancel(Instant now) {
    if (status == PurchaseOrderStatus.CANCELLED) return;
    if (status != PurchaseOrderStatus.DRAFT
        && status != PurchaseOrderStatus.SUBMITTED
        && status != PurchaseOrderStatus.PARTIALLY_RECEIVED)
      throw new InvalidPurchaseOrderStateException(
          "Purchase order cannot be cancelled in status " + status);
    status = PurchaseOrderStatus.CANCELLED;
    cancelledAt = now;
  }

  public void applyReceipt(boolean complete, Instant now) {
    if (status == PurchaseOrderStatus.CANCELLED)
      throw new InvalidPurchaseOrderStateException("Cancelled purchase orders cannot receive");
    status = complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    if (complete) completedAt = now;
  }

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID().toString();
    Instant n = TimePrecision.databasePrecision(Instant.now());
    if (createdAt == null) createdAt = n;
    updatedAt = n;
    validate();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = TimePrecision.databasePrecision(Instant.now());
    validate();
  }

  private void validate() {
    if (supplier == null || warehouse == null || status == null || items == null || items.isEmpty())
      throw new IllegalStateException("Purchase order requires references and items");
    Money.requireDatabaseValue(totalAmount, "totalAmount");
  }
}
