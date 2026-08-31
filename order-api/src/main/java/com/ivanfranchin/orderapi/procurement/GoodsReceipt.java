package com.ivanfranchin.orderapi.procurement;

import com.ivanfranchin.orderapi.config.TimePrecision;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(
    name = "goods_receipts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_goods_receipts_order_request",
            columnNames = {"purchase_order_id", "client_request_id"}))
public class GoodsReceipt {
  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PurchaseOrder purchaseOrder;

  @Column(name = "client_request_id", nullable = false, length = 128)
  private String clientRequestId;

  @Column(nullable = false, length = 255)
  private String receivedByUsername;

  @Column(nullable = false, updatable = false)
  private Instant receivedAt;

  @Column(name = "payload_hash", nullable = false, length = 64)
  private String payloadHash;

  @OrderBy("lineNumber ASC")
  @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<GoodsReceiptItem> items = new ArrayList<>();

  public GoodsReceipt(String id, PurchaseOrder po, String key, String user, String hash) {
    this.id = id;
    purchaseOrder = po;
    clientRequestId = key;
    receivedByUsername = user;
    payloadHash = hash;
  }

  public void addItem(PurchaseOrderItem item, long quantity, long before, long after, int line) {
    items.add(new GoodsReceiptItem(this, item, quantity, before, after, line));
  }

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID().toString();
    if (receivedAt == null) receivedAt = TimePrecision.databasePrecision(Instant.now());
  }
}
