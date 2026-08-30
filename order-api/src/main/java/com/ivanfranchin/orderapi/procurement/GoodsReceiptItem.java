package com.ivanfranchin.orderapi.procurement;

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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(
    name = "goods_receipt_items",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_goods_receipt_items_line",
            columnNames = {"goods_receipt_id", "line_number"}))
public class GoodsReceiptItem {
  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "goods_receipt_id", nullable = false)
  private GoodsReceipt goodsReceipt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_item_id", nullable = false)
  private PurchaseOrderItem purchaseOrderItem;

  @Column(nullable = false)
  @NotNull @Positive private long quantity;

  @Column(name = "before_on_hand", nullable = false)
  private long beforeOnHand;

  @Column(name = "after_on_hand", nullable = false)
  private long afterOnHand;

  @Column(name = "line_number", nullable = false)
  private int lineNumber;

  GoodsReceiptItem(GoodsReceipt r, PurchaseOrderItem i, long q, long b, long a, int l) {
    id = UUID.randomUUID().toString();
    goodsReceipt = r;
    purchaseOrderItem = i;
    quantity = q;
    beforeOnHand = b;
    afterOnHand = a;
    lineNumber = l;
  }
}
