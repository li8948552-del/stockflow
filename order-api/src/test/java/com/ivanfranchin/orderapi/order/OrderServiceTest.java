package com.ivanfranchin.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderItemRequest;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderRequest;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.user.UserService;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@Import(OrderService.class)
class OrderServiceTest {
  @MockitoBean private OrderRepository orderRepository;
  @MockitoBean private UserService userService;
  @MockitoBean private InventoryService inventoryService;
  @Autowired private OrderService orderService;

  private User user;
  private Warehouse warehouse;
  private Product product;

  @BeforeEach
  void setUp() {
    user = new User("alice", "secret", "Alice", "alice@example.com", Role.USER);
    user.setId(1L);
    warehouse = new Warehouse("WH-1", "Main", "Sydney");
    ReflectionTestUtils.setField(warehouse, "id", "warehouse-id");
    product = new Product("SKU-1", "Widget", new BigDecimal("12.50"), 5);
    ReflectionTestUtils.setField(product, "id", "product-id");
  }

  @Test
  void createOrderReservesStockAndUsesPriceSnapshot() {
    when(userService.validateAndGetUserByUsernameForUpdate("alice")).thenReturn(user);
    when(inventoryService.reserveForOrder(
            org.mockito.ArgumentMatchers.eq("warehouse-id"),
            org.mockito.ArgumentMatchers.eq(Map.of("product-id", 2L)),
            any(String.class),
            org.mockito.ArgumentMatchers.eq("alice")))
        .thenReturn(
            new InventoryService.ReservationBatch(
                warehouse, List.of(new InventoryService.ReservedProduct(product, 2))));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Order result =
        orderService.createOrder(
            new CreateOrderRequest(
                "warehouse-id", List.of(new CreateOrderItemRequest("product-id", 2L))),
            "alice");

    assertThat(result.getStatus()).isEqualTo(OrderStatus.RESERVED);
    assertThat(result.getItems())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getUnitPrice()).isEqualByComparingTo("12.50");
              assertThat(item.getLineTotal()).isEqualByComparingTo("25.00");
            });
    assertThat(result.getTotalAmount()).isEqualByComparingTo("25.00");
    assertThat(result.getExpiresAt())
        .isAfter(result.getCreatedAt() == null ? java.time.Instant.EPOCH : result.getCreatedAt());
  }

  @Test
  void duplicateProductIsRejectedBeforeReservation() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            "warehouse-id",
            List.of(
                new CreateOrderItemRequest("product-id", 1L),
                new CreateOrderItemRequest("product-id", 2L)));

    assertThatThrownBy(() -> orderService.createOrder(request, "alice"))
        .isInstanceOf(DuplicateOrderProductException.class);
    verify(inventoryService, never()).reserveForOrder(any(), any(), any(), any());
  }

  @Test
  void emptyOrderIsRejected() {
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    new CreateOrderRequest("warehouse-id", List.of()), "alice"))
        .isInstanceOf(EmptyOrderException.class);
  }

  @Test
  void userCannotReadAnotherUsersOrder() {
    Order order = order("order-id", user);
    when(orderRepository.findDetailedById("order-id")).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.getOrder("order-id", "bob", Role.USER))
        .isInstanceOf(OrderAccessDeniedException.class);
  }

  @Test
  void adminCanReadAnotherUsersOrder() {
    Order order = order("order-id", user);
    when(orderRepository.findDetailedById("order-id")).thenReturn(Optional.of(order));
    assertThat(orderService.getOrder("order-id", "admin", Role.ADMIN)).isSameAs(order);
  }

  @Test
  void cancellationReleasesReservationAndIsIdempotent() {
    Order order = order("order-id", user);
    when(orderRepository.findByIdForUpdate("order-id")).thenReturn(Optional.of(order));
    when(orderRepository.findDetailedById("order-id")).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    Order cancelled = orderService.cancelOrder("order-id", "alice", Role.USER);
    Order repeated = orderService.cancelOrder("order-id", "alice", Role.USER);

    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(repeated).isSameAs(order);
    verify(inventoryService)
        .releaseForOrder("warehouse-id", Map.of("product-id", 2L), "order-id", "alice");
  }

  @Test
  void nonReservedOrderCannotBeCancelled() {
    Order order = order("order-id", user);
    ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);
    when(orderRepository.findByIdForUpdate("order-id")).thenReturn(Optional.of(order));
    when(orderRepository.findDetailedById("order-id")).thenReturn(Optional.of(order));
    assertThatThrownBy(() -> orderService.cancelOrder("order-id", "alice", Role.USER))
        .isInstanceOf(InvalidOrderStatusException.class);
    verify(inventoryService, never()).releaseForOrder(any(), any(), any(), any());
  }

  @Test
  void regularUserListIsAlwaysScopedToOwner() {
    orderService.getOrders("alice", Role.USER, 99L, OrderStatus.RESERVED, "warehouse-id");
    verify(orderRepository).findOrders("alice", null, OrderStatus.RESERVED, "warehouse-id");
  }

  @Test
  void adminListSupportsFilters() {
    orderService.getOrders("admin", Role.ADMIN, 99L, OrderStatus.CANCELLED, "warehouse-id");
    verify(orderRepository).findOrders(null, 99L, OrderStatus.CANCELLED, "warehouse-id");
  }

  @Test
  void ownerCanConfirmSimulatedPaymentAndRepeatedPaymentIsIdempotent() {
    Order order = order("order-id", user);
    when(orderRepository.findByIdForUpdate("order-id")).thenReturn(Optional.of(order));
    when(orderRepository.findDetailedById("order-id")).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    Order paid = orderService.payOrder("order-id", "alice", Role.USER);
    String reference = paid.getPaymentReference();
    Order repeated = orderService.payOrder("order-id", "alice", Role.USER);

    assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(paid.getPaidAt()).isNotNull();
    assertThat(reference).startsWith("PAY-");
    assertThat(repeated.getPaymentReference()).isEqualTo(reference);
    verify(orderRepository).saveAndFlush(order);
  }

  @Test
  void adminCanShipPaidOrderThroughInventoryService() {
    Order order = order("order-id", user);
    order.markPaid(Instant.now().minusSeconds(1), "PAY-test");
    when(orderRepository.findByIdForUpdate("order-id")).thenReturn(Optional.of(order));
    when(orderRepository.findDetailedById("order-id")).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    Order shipped = orderService.shipOrder("order-id", "admin", Role.ADMIN);

    assertThat(shipped.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    assertThat(shipped.getShippedAt()).isNotNull();
    verify(inventoryService)
        .shipForOrder("warehouse-id", Map.of("product-id", 2L), "order-id", "admin");
  }

  private Order order(String id, User owner) {
    Order order = new Order(id, owner, warehouse, java.time.Instant.now().plusSeconds(1800));
    order.addItem(product, 2, 1);
    return order;
  }
}
