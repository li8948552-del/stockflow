package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.procurement.GoodsReceipt;
import java.time.Instant;
import java.util.List;

public record GoodsReceiptDto(
    String id,
    String purchaseOrderId,
    String clientRequestId,
    String receivedByUsername,
    Instant receivedAt,
    List<Item> items) {
  public record Item(
      String purchaseOrderItemId,
      int lineNumber,
      long quantity,
      long beforeOnHand,
      long afterOnHand) {}

  public static GoodsReceiptDto from(GoodsReceipt r) {
    return new GoodsReceiptDto(
        r.getId(),
        r.getPurchaseOrder().getId(),
        r.getClientRequestId(),
        r.getReceivedByUsername(),
        r.getReceivedAt(),
        r.getItems().stream()
            .map(
                i ->
                    new Item(
                        i.getPurchaseOrderItem().getId(),
                        i.getLineNumber(),
                        i.getQuantity(),
                        i.getBeforeOnHand(),
                        i.getAfterOnHand()))
            .toList());
  }
}
