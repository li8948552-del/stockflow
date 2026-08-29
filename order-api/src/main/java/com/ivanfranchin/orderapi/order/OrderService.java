package com.ivanfranchin.orderapi.order;

import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderItemRequest;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderRequest;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderService {
  private static final Duration RESERVATION_DURATION = Duration.ofMinutes(30);

  private final OrderRepository orderRepository;
  private final UserService userService;
  private final InventoryService inventoryService;

  @Transactional(readOnly = true)
  public List<Order> getOrders(
      String username, Role role, Long userId, OrderStatus status, String warehouseId) {
    String owner = role == Role.ADMIN ? null : username;
    return orderRepository.findOrders(
        owner, role == Role.ADMIN ? userId : null, status, warehouseId);
  }

  @Transactional(readOnly = true)
  public Order getOrder(String id, String username, Role role) {
    Order order = findDetailed(id);
    requireOwnerOrAdmin(order, username, role);
    return order;
  }

  @Transactional
  public Order createOrder(CreateOrderRequest request, String username) {
    Map<String, Long> quantities = validateAndIndex(request.items());
    // Global order: User, Warehouse, sorted Products, then matching Inventories.
    User user = userService.validateAndGetUserByUsernameForUpdate(username);
    String orderId = UUID.randomUUID().toString();
    InventoryService.ReservationBatch reservation =
        inventoryService.reserveForOrder(request.warehouseId(), quantities, orderId, username);
    Order order =
        new Order(orderId, user, reservation.warehouse(), Instant.now().plus(RESERVATION_DURATION));
    Map<String, InventoryService.ReservedProduct> reservedByProduct =
        reservation.products().stream()
            .collect(Collectors.toMap(item -> item.product().getId(), item -> item));
    int lineNumber = 1;
    for (Map.Entry<String, Long> requested : quantities.entrySet()) {
      InventoryService.ReservedProduct reserved = reservedByProduct.get(requested.getKey());
      order.addItem(reserved.product(), requested.getValue(), lineNumber++);
    }
    return orderRepository.saveAndFlush(order);
  }

  @Transactional
  public Order cancelOrder(String id, String username, Role role) {
    orderRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    Order order = orderRepository.findDetailedById(id).orElseThrow(() -> notFound(id));
    requireOwnerOrAdmin(order, username, role);
    if (order.getStatus() == OrderStatus.CANCELLED) return order;
    if (order.getStatus() != OrderStatus.RESERVED) {
      throw new InvalidOrderStatusException(
          "Order in status %s cannot be cancelled".formatted(order.getStatus()));
    }
    Map<String, Long> quantities =
        order.getItems().stream()
            .collect(
                Collectors.toMap(
                    item -> item.getProduct().getId(),
                    OrderItem::getQuantity,
                    (left, right) -> left,
                    LinkedHashMap::new));
    inventoryService.releaseForOrder(
        order.getWarehouse().getId(), quantities, order.getId(), username);
    order.cancel();
    return orderRepository.saveAndFlush(order);
  }

  public long countOrders() {
    return orderRepository.count();
  }

  private Map<String, Long> validateAndIndex(List<CreateOrderItemRequest> items) {
    if (items == null || items.isEmpty())
      throw new EmptyOrderException("Order must contain at least one item");
    Map<String, Long> quantities = new LinkedHashMap<>();
    for (CreateOrderItemRequest item : items) {
      if (item == null
          || item.productId() == null
          || item.productId().isBlank()
          || item.quantity() == null
          || item.quantity() <= 0) {
        throw new InvalidOrderException("Order item product and positive quantity are required");
      }
      if (quantities.putIfAbsent(item.productId(), item.quantity()) != null) {
        throw new DuplicateOrderProductException(
            "Product %s appears more than once in the order".formatted(item.productId()));
      }
    }
    return quantities;
  }

  private Order findDetailed(String id) {
    return orderRepository.findDetailedById(id).orElseThrow(() -> notFound(id));
  }

  private void requireOwnerOrAdmin(Order order, String username, Role role) {
    if (role != Role.ADMIN && !order.isOwnedBy(username)) {
      throw new OrderAccessDeniedException("You cannot access this order");
    }
  }

  private OrderNotFoundException notFound(String id) {
    return new OrderNotFoundException("Order with id %s not found".formatted(id));
  }
}
