package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.order.Order;
import com.ivanfranchin.orderapi.order.OrderItem;
import com.ivanfranchin.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
    String id,
    UserSummary user,
    WarehouseSummary warehouse,
    OrderStatus status,
    List<Item> items,
    BigDecimal totalAmount,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    Long version) {
  public record UserSummary(Long id, String username) {}

  public record WarehouseSummary(String id, String code, String name) {}

  public record Item(
      String id,
      String productId,
      String productSku,
      String productName,
      int lineNumber,
      long quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal) {
    static Item from(OrderItem item) {
      return new Item(
          item.getId(),
          item.getProduct().getId(),
          item.getProduct().getSku(),
          item.getProduct().getName(),
          item.getLineNumber(),
          item.getQuantity(),
          item.getUnitPrice(),
          item.getLineTotal());
    }
  }

  public static OrderDto from(Order order) {
    return new OrderDto(
        order.getId(),
        new UserSummary(order.getUser().getId(), order.getUser().getUsername()),
        new WarehouseSummary(
            order.getWarehouse().getId(),
            order.getWarehouse().getWarehouseCode(),
            order.getWarehouse().getName()),
        order.getStatus(),
        order.getItems().stream().map(Item::from).toList(),
        order.getTotalAmount(),
        order.getExpiresAt(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getVersion());
  }
}
