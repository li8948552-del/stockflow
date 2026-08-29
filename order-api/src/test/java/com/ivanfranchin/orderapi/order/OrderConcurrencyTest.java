package com.ivanfranchin.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ivanfranchin.orderapi.inventory.InsufficientInventoryException;
import com.ivanfranchin.orderapi.inventory.Inventory;
import com.ivanfranchin.orderapi.inventory.InventoryMovementRepository;
import com.ivanfranchin.orderapi.inventory.InventoryMovementType;
import com.ivanfranchin.orderapi.inventory.InventoryRepository;
import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderItemRequest;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderRequest;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.user.UserRepository;
import com.ivanfranchin.orderapi.user.UserService;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Import({
  OrderService.class,
  UserService.class,
  InventoryService.class,
  OrderExpirationProcessor.class,
  OrderConcurrencyTest.LockTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderConcurrencyTest {
  @Autowired private OrderService orderService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private WarehouseRepository warehouseRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryMovementRepository movementRepository;
  @Autowired private InventoryService inventoryService;
  @Autowired private WarehouseLockCoordinator warehouseLockCoordinator;
  @Autowired private OrderLockCoordinator orderLockCoordinator;
  @Autowired private OrderExpirationProcessor expirationProcessor;

  @AfterEach
  void clearCoordinator() {
    warehouseLockCoordinator.clear();
    orderLockCoordinator.clear();
  }

  @Test
  void twoUsersCompetingForLastStockYieldExactlyOneReservation() throws Exception {
    Setup setup = setup(5);
    User second = saveUser("bob");
    CreateOrderRequest request = request(setup, List.of(setup.first()), List.of(5L));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    warehouseLockCoordinator.blockUntilReleased(2);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Object> first =
          executor.submit(() -> createAfterBarrier(request, setup.user(), ready, start));
      Future<Object> other =
          executor.submit(() -> createAfterBarrier(request, second, ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(warehouseLockCoordinator.awaitReached()).isTrue();
      assertThat(warehouseLockCoordinator.transactionalCalls()).isEqualTo(2);
      warehouseLockCoordinator.release();
      List<Object> results =
          List.of(first.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));

      assertThat(results).filteredOn(Order.class::isInstance).hasSize(1);
      assertThat(results).filteredOn(InsufficientInventoryException.class::isInstance).hasSize(1);
    }
    Inventory inventory = inventoryRepository.findById(setup.inventory().getId()).orElseThrow();
    assertThat(inventory.getReserved()).isEqualTo(5);
    assertThat(orderRepository.count()).isEqualTo(1);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RESERVATION)
        .hasSize(1);
  }

  @Test
  void oppositeClientItemOrderUsesStableLocksAndBothOrdersComplete() throws Exception {
    Setup setup = setup(20);
    User second = saveUser("bob");
    CreateOrderRequest forward =
        request(setup, List.of(setup.first(), setup.second()), List.of(2L, 3L));
    CreateOrderRequest reverse =
        request(setup, List.of(setup.second(), setup.first()), List.of(4L, 5L));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    warehouseLockCoordinator.blockUntilReleased(2);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Object> first =
          executor.submit(() -> createAfterBarrier(forward, setup.user(), ready, start));
      Future<Object> other =
          executor.submit(() -> createAfterBarrier(reverse, second, ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(warehouseLockCoordinator.awaitReached()).isTrue();
      assertThat(warehouseLockCoordinator.transactionalCalls()).isEqualTo(2);
      warehouseLockCoordinator.release();
      assertThat(first.get(10, TimeUnit.SECONDS)).isInstanceOf(Order.class);
      assertThat(other.get(10, TimeUnit.SECONDS)).isInstanceOf(Order.class);
    }
    assertThat(orderRepository.count()).isEqualTo(2);
    assertThat(orderRepository.findAll())
        .allSatisfy(
            order ->
                assertThat(orderRepository.findDetailedById(order.getId()).orElseThrow().getItems())
                    .hasSize(2));
    assertThat(inventoryRepository.findById(setup.inventory().getId()).orElseThrow().getReserved())
        .isEqualTo(7);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RESERVATION)
        .hasSize(4);
  }

  @Test
  void receiptAndReservationSerializeWithoutLostUpdates() throws Exception {
    Setup setup = setup(10);
    CreateOrderRequest request = request(setup, List.of(setup.first()), List.of(4L));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    warehouseLockCoordinator.blockUntilReleased(2);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> order =
          executor.submit(() -> createAfterBarrier(request, setup.user(), ready, start));
      Future<?> receipt =
          executor.submit(
              () -> {
                awaitBarrier(ready, start);
                return inventoryService.receive(
                    setup.first().getId(), setup.warehouse().getId(), 5, "PO", "admin");
              });
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(warehouseLockCoordinator.awaitReached()).isTrue();
      assertThat(warehouseLockCoordinator.transactionalCalls()).isEqualTo(2);
      warehouseLockCoordinator.release();
      assertThat(order.get(10, TimeUnit.SECONDS)).isInstanceOf(Order.class);
      receipt.get(10, TimeUnit.SECONDS);
    }
    Inventory inventory = inventoryRepository.findById(setup.inventory().getId()).orElseThrow();
    assertThat(inventory.getOnHand()).isEqualTo(15);
    assertThat(inventory.getReserved()).isEqualTo(4);
    assertThat(orderRepository.count()).isEqualTo(1);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RECEIPT)
        .hasSize(1);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RESERVATION)
        .hasSize(1);
  }

  @Test
  void simultaneousCancellationReleasesEachItemExactlyOnce() throws Exception {
    Setup setup = setup(10);
    Order order =
        orderService.createOrder(
            request(setup, List.of(setup.first(), setup.second()), List.of(2L, 3L)),
            setup.user().getUsername());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    orderLockCoordinator.blockUntilReleased(2);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Order> first =
          executor.submit(
              () -> cancelAfterBarrier(order.getId(), setup.user().getUsername(), ready, start));
      Future<Order> second =
          executor.submit(
              () -> cancelAfterBarrier(order.getId(), setup.user().getUsername(), ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(orderLockCoordinator.awaitReached()).isTrue();
      assertThat(orderLockCoordinator.transactionalCalls()).isEqualTo(2);
      orderLockCoordinator.release();
      assertThat(first.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(second.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    } finally {
      orderLockCoordinator.release();
    }

    assertThat(inventoryRepository.findById(setup.inventory().getId()).orElseThrow().getOnHand())
        .isEqualTo(10);
    assertThat(inventoryRepository.findById(setup.inventory().getId()).orElseThrow().getReserved())
        .isZero();
    assertThat(inventoryRepository.findAll())
        .filteredOn(inventory -> inventory.getWarehouse().getId().equals(setup.warehouse().getId()))
        .allSatisfy(inventory -> assertThat(inventory.getReserved()).isZero());
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .hasSize(2)
        .allSatisfy(movement -> assertThat(movement.getReference()).isEqualTo(order.getId()));
  }

  @Test
  void doublePayIsIdempotentAndDoesNotTouchInventory() throws Exception {
    Setup setup = setup(10);
    Order order =
        orderService.createOrder(request(setup, List.of(setup.first()), List.of(2L)), "alice");
    orderLockCoordinator.blockUntilReleased(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Order> first =
          executor.submit(() -> orderService.payOrder(order.getId(), "alice", Role.USER));
      Future<Order> second =
          executor.submit(() -> orderService.payOrder(order.getId(), "alice", Role.USER));
      assertThat(orderLockCoordinator.awaitReached()).isTrue();
      orderLockCoordinator.release();
      Order paid1 = first.get(10, TimeUnit.SECONDS);
      Order paid2 = second.get(10, TimeUnit.SECONDS);
      assertThat(paid1.getStatus()).isEqualTo(OrderStatus.PAID);
      assertThat(paid2.getStatus()).isEqualTo(OrderStatus.PAID);
      assertThat(paid1.getPaymentReference()).isEqualTo(paid2.getPaymentReference());
      assertThat(paid1.getPaidAt()).isEqualTo(paid2.getPaidAt());
    } finally {
      orderLockCoordinator.release();
    }
    Order persisted = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(inventoryRepository.findById(setup.inventory().getId()).orElseThrow())
        .satisfies(
            i -> {
              assertThat(i.getOnHand()).isEqualTo(10);
              assertThat(i.getReserved()).isEqualTo(2);
            });
    assertThat(movementRepository.findAll())
        .filteredOn(m -> m.getType() == InventoryMovementType.RESERVATION)
        .hasSize(1);
  }

  @Test
  void doubleExpirationProcessorReleasesOnlyOnce() throws Exception {
    Setup setup = setup(10);
    Order order =
        orderService.createOrder(request(setup, List.of(setup.first()), List.of(2L)), "alice");
    ReflectionTestUtils.setField(order, "expiresAt", java.time.Instant.now().minusSeconds(1));
    orderRepository.saveAndFlush(order);
    orderLockCoordinator.blockUntilReleased(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> first = executor.submit(() -> expirationProcessor.process(order.getId()));
      Future<?> second = executor.submit(() -> expirationProcessor.process(order.getId()));
      assertThat(orderLockCoordinator.awaitReached()).isTrue();
      orderLockCoordinator.release();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    } finally {
      orderLockCoordinator.release();
    }
    Order persisted = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    assertThat(inventoryRepository.findById(setup.inventory().getId()).orElseThrow().getReserved())
        .isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(m -> m.getType() == InventoryMovementType.RELEASE)
        .hasSize(1);
  }

  @Test
  void payAndCancelCompetitionHasSingleDeterministicWinner() throws Exception {
    Setup setup = setup(10);
    Order order =
        orderService.createOrder(request(setup, List.of(setup.first()), List.of(2L)), "alice");
    orderLockCoordinator.blockUntilReleased(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Object> pay =
          executor.submit(
              () -> capture(() -> orderService.payOrder(order.getId(), "alice", Role.USER)));
      Future<Object> cancel =
          executor.submit(
              () -> capture(() -> orderService.cancelOrder(order.getId(), "alice", Role.USER)));
      assertThat(orderLockCoordinator.awaitReached()).isTrue();
      orderLockCoordinator.release();
      Object first = pay.get(10, TimeUnit.SECONDS);
      Object second = cancel.get(10, TimeUnit.SECONDS);
      assertThat(List.of(first, second)).anyMatch(value -> value instanceof Order);
      assertThat(List.of(first, second)).anyMatch(value -> value instanceof RuntimeException);
    } finally {
      orderLockCoordinator.release();
    }
    Order persisted = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isIn(OrderStatus.PAID, OrderStatus.CANCELLED);
    if (persisted.getStatus() == OrderStatus.PAID) {
      assertThat(
              inventoryRepository.findById(setup.inventory().getId()).orElseThrow().getReserved())
          .isEqualTo(2);
      assertThat(movementRepository.findAll())
          .filteredOn(m -> m.getType() == InventoryMovementType.RELEASE)
          .isEmpty();
    } else {
      assertThat(
              inventoryRepository.findById(setup.inventory().getId()).orElseThrow().getReserved())
          .isZero();
      assertThat(movementRepository.findAll())
          .filteredOn(m -> m.getType() == InventoryMovementType.RELEASE)
          .hasSize(1);
    }
  }

  private Object capture(java.util.concurrent.Callable<?> operation) {
    try {
      return operation.call();
    } catch (Exception exception) {
      return exception;
    }
  }

  @Test
  void doubleShipIsIdempotentAndDeductsInventoryOnce() throws Exception {
    Setup setup = setup(10);
    Order order =
        orderService.createOrder(request(setup, List.of(setup.first()), List.of(2L)), "alice");
    orderService.payOrder(order.getId(), "alice", Role.USER);
    orderLockCoordinator.blockUntilReleased(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Order> first =
          executor.submit(() -> orderService.shipOrder(order.getId(), "admin", Role.ADMIN));
      Future<Order> second =
          executor.submit(() -> orderService.shipOrder(order.getId(), "admin", Role.ADMIN));
      assertThat(orderLockCoordinator.awaitReached()).isTrue();
      orderLockCoordinator.release();
      assertThat(first.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(OrderStatus.SHIPPED);
      assertThat(second.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    } finally {
      orderLockCoordinator.release();
    }
    Inventory inventory = inventoryRepository.findById(setup.inventory().getId()).orElseThrow();
    assertThat(inventory.getOnHand()).isEqualTo(8);
    assertThat(inventory.getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(m -> m.getType() == InventoryMovementType.SHIPMENT)
        .hasSize(1);
  }

  @Test
  void payAndExpirationCompetitionLeavesOneValidTerminalState() throws Exception {
    Setup setup = setup(10);
    Order order =
        orderService.createOrder(request(setup, List.of(setup.first()), List.of(2L)), "alice");
    ReflectionTestUtils.setField(order, "expiresAt", java.time.Instant.now().plusSeconds(60));
    orderRepository.saveAndFlush(order);
    orderLockCoordinator.blockUntilReleased(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Object> pay =
          executor.submit(
              () -> capture(() -> orderService.payOrder(order.getId(), "alice", Role.USER)));
      Future<Object> expire =
          executor.submit(
              () ->
                  capture(
                      () -> {
                        expirationProcessor.process(order.getId());
                        return null;
                      }));
      assertThat(orderLockCoordinator.awaitReached()).isTrue();
      orderLockCoordinator.release();
      pay.get(10, TimeUnit.SECONDS);
      expire.get(10, TimeUnit.SECONDS);
    } finally {
      orderLockCoordinator.release();
    }
    Order persisted = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isIn(OrderStatus.PAID, OrderStatus.RESERVED);
    if (persisted.getStatus() == OrderStatus.PAID) {
      assertThat(persisted.getPaidAt()).isNotNull();
      assertThat(
              inventoryRepository.findById(setup.inventory().getId()).orElseThrow().getReserved())
          .isEqualTo(2);
    }
  }

  @Test
  void shipmentAndReceiptPreserveBothDeltas() throws Exception {
    Setup setup = setup(10);
    Order order =
        orderService.createOrder(request(setup, List.of(setup.first()), List.of(2L)), "alice");
    orderService.payOrder(order.getId(), "alice", Role.USER);
    warehouseLockCoordinator.blockUntilReleased(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Order> ship =
          executor.submit(() -> orderService.shipOrder(order.getId(), "admin", Role.ADMIN));
      Future<Inventory> receipt =
          executor.submit(
              () ->
                  inventoryService.receive(
                      setup.first().getId(), setup.warehouse().getId(), 5, "PO", "admin"));
      assertThat(warehouseLockCoordinator.awaitReached()).isTrue();
      warehouseLockCoordinator.release();
      assertThat(ship.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(OrderStatus.SHIPPED);
      receipt.get(10, TimeUnit.SECONDS);
    } finally {
      warehouseLockCoordinator.release();
    }
    Inventory inventory = inventoryRepository.findById(setup.inventory().getId()).orElseThrow();
    assertThat(inventory.getOnHand()).isEqualTo(13);
    assertThat(inventory.getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(m -> m.getType() == InventoryMovementType.SHIPMENT)
        .hasSize(1);
    assertThat(movementRepository.findAll())
        .filteredOn(m -> m.getType() == InventoryMovementType.RECEIPT)
        .hasSize(1);
  }

  private Order cancelAfterBarrier(
      String orderId, String username, CountDownLatch ready, CountDownLatch start) {
    awaitBarrier(ready, start);
    return orderService.cancelOrder(orderId, username, Role.USER);
  }

  private Object createAfterBarrier(
      CreateOrderRequest request, User user, CountDownLatch ready, CountDownLatch start) {
    awaitBarrier(ready, start);
    try {
      return orderService.createOrder(request, user.getUsername());
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private void awaitBarrier(CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private Setup setup(long stock) {
    User user = saveUser("alice");
    Warehouse warehouse =
        warehouseRepository.saveAndFlush(new Warehouse("WH-CONCURRENT", "Main", "Sydney"));
    Product first =
        productRepository.saveAndFlush(
            new Product("SKU-CONCURRENT-A", "A", new BigDecimal("1.00"), 1));
    Product second =
        productRepository.saveAndFlush(
            new Product("SKU-CONCURRENT-B", "B", new BigDecimal("2.00"), 1));
    Inventory inventory = inventoryRepository.saveAndFlush(new Inventory(first, warehouse, stock));
    inventoryRepository.saveAndFlush(new Inventory(second, warehouse, stock));
    return new Setup(user, warehouse, first, second, inventory);
  }

  private User saveUser(String username) {
    return userRepository.saveAndFlush(
        new User(username, "secret", username, username + "@example.com", Role.USER));
  }

  private CreateOrderRequest request(Setup setup, List<Product> products, List<Long> quantities) {
    java.util.ArrayList<CreateOrderItemRequest> items = new java.util.ArrayList<>();
    for (int index = 0; index < products.size(); index++) {
      items.add(new CreateOrderItemRequest(products.get(index).getId(), quantities.get(index)));
    }
    return new CreateOrderRequest(setup.warehouse().getId(), items);
  }

  private record Setup(
      User user, Warehouse warehouse, Product first, Product second, Inventory inventory) {}

  @TestConfiguration
  @EnableAspectJAutoProxy
  static class LockTestConfiguration {
    @Bean
    WarehouseLockCoordinator warehouseLockCoordinator() {
      return new WarehouseLockCoordinator();
    }

    @Bean
    WarehouseLockAspect warehouseLockAspect(
        @Qualifier("warehouseLockCoordinator") WarehouseLockCoordinator coordinator) {
      return new WarehouseLockAspect(coordinator);
    }

    @Bean
    OrderLockCoordinator orderLockCoordinator() {
      return new OrderLockCoordinator();
    }

    @Bean
    OrderLockAspect orderLockAspect(
        @Qualifier("orderLockCoordinator") OrderLockCoordinator coordinator) {
      return new OrderLockAspect(coordinator);
    }
  }

  static class OrderLockCoordinator extends WarehouseLockCoordinator {}

  static class WarehouseLockCoordinator {
    private volatile boolean enabled;
    private volatile CountDownLatch reached = new CountDownLatch(0);
    private volatile CountDownLatch release = new CountDownLatch(0);
    private final AtomicInteger transactionalCalls = new AtomicInteger();

    void blockUntilReleased(int expectedCalls) {
      transactionalCalls.set(0);
      reached = new CountDownLatch(expectedCalls);
      release = new CountDownLatch(1);
      enabled = true;
    }

    boolean awaitReached() throws InterruptedException {
      return reached.await(5, TimeUnit.SECONDS);
    }

    int transactionalCalls() {
      return transactionalCalls.get();
    }

    void release() {
      release.countDown();
    }

    void clear() {
      release();
      enabled = false;
      reached = new CountDownLatch(0);
      release = new CountDownLatch(0);
      transactionalCalls.set(0);
    }

    Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
      if (!enabled) return joinPoint.proceed();
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        transactionalCalls.incrementAndGet();
      }
      reached.countDown();
      if (!release.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to release warehouse lock calls");
      }
      return joinPoint.proceed();
    }
  }

  @Aspect
  static class WarehouseLockAspect {
    private final WarehouseLockCoordinator coordinator;

    WarehouseLockAspect(WarehouseLockCoordinator coordinator) {
      this.coordinator = coordinator;
    }

    @Around(
        "execution(* com.ivanfranchin.orderapi.warehouse.WarehouseRepository.findByIdForUpdate(..))")
    Object synchronizeWarehouseLock(ProceedingJoinPoint joinPoint) throws Throwable {
      return coordinator.intercept(joinPoint);
    }
  }

  @Aspect
  static class OrderLockAspect {
    private final OrderLockCoordinator coordinator;

    OrderLockAspect(OrderLockCoordinator coordinator) {
      this.coordinator = coordinator;
    }

    @Around("execution(* com.ivanfranchin.orderapi.order.OrderRepository.findByIdForUpdate(..))")
    Object synchronizeOrderLock(ProceedingJoinPoint joinPoint) throws Throwable {
      return coordinator.intercept(joinPoint);
    }
  }
}
