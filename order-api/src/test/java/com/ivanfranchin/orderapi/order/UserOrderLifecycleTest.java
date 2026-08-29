package com.ivanfranchin.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ivanfranchin.orderapi.inventory.Inventory;
import com.ivanfranchin.orderapi.inventory.InventoryMovementRepository;
import com.ivanfranchin.orderapi.inventory.InventoryRepository;
import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderItemRequest;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderRequest;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.user.UserHasOrdersException;
import com.ivanfranchin.orderapi.user.UserNotFoundException;
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
import java.util.function.Supplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Import({
  OrderService.class,
  UserService.class,
  InventoryService.class,
  UserOrderLifecycleTest.LockTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserOrderLifecycleTest {
  @Autowired private OrderService orderService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private WarehouseRepository warehouseRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryMovementRepository movementRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserLockCoordinator userLockCoordinator;

  @AfterEach
  void clearCoordinator() {
    userLockCoordinator.clear();
  }

  @Test
  void reservedOrderPreventsUserDeletionWithoutChangingHistoryOrReservation() {
    Fixture fixture = fixture();
    Order order = createOrder(fixture);

    assertThatThrownBy(() -> userService.deleteUser(fixture.user().getUsername(), "admin"))
        .isInstanceOf(UserHasOrdersException.class);

    assertThat(userRepository.findById(fixture.user().getId())).isPresent();
    assertThat(orderRepository.findDetailedById(order.getId())).isPresent();
    assertThat(orderRepository.findDetailedById(order.getId()).orElseThrow().getItems()).hasSize(1);
    assertThat(reload(fixture).getReserved()).isEqualTo(3);
  }

  @Test
  void cancelledOrderStillPreventsUserDeletionAndRetainsOrderItems() {
    Fixture fixture = fixture();
    Order order = createOrder(fixture);
    orderService.cancelOrder(order.getId(), fixture.user().getUsername(), Role.USER);

    assertThatThrownBy(() -> userService.deleteUser(fixture.user().getUsername(), "admin"))
        .isInstanceOf(UserHasOrdersException.class);

    Order history = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(history.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(history.getItems()).hasSize(1);
    assertThat(reload(fixture).getReserved()).isZero();
  }

  @Test
  void userWithoutOrdersCanBeDeleted() {
    User user = saveUser("unused");

    userService.deleteUser(user.getUsername(), "admin");

    assertThat(userRepository.findById(user.getId())).isEmpty();
  }

  @Test
  void orderCreationWinningUserLockMakesDeletionFailWithPreservedHistory() throws Exception {
    Fixture fixture = fixture();
    userLockCoordinator.firstParticipant("create");

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Order> create =
          executor.submit(
              () -> userLockCoordinator.asParticipant("create", () -> createOrder(fixture)));
      assertThat(userLockCoordinator.awaitFirstLocked()).isTrue();
      Future<Object> delete =
          executor.submit(
              () ->
                  userLockCoordinator.asParticipant(
                      "delete",
                      () -> {
                        try {
                          userService.deleteUser(fixture.user().getUsername(), "admin");
                          return "deleted";
                        } catch (RuntimeException exception) {
                          return exception;
                        }
                      }));
      assertThat(userLockCoordinator.awaitSecondReached()).isTrue();
      assertThat(userLockCoordinator.transactionalCalls()).isEqualTo(2);
      userLockCoordinator.releaseFirst();

      Order order = create.get(10, TimeUnit.SECONDS);
      assertThat(delete.get(10, TimeUnit.SECONDS)).isInstanceOf(UserHasOrdersException.class);
      assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
    } finally {
      userLockCoordinator.releaseFirst();
    }

    assertThat(userRepository.findById(fixture.user().getId())).isPresent();
    assertThat(orderRepository.count()).isEqualTo(1);
    assertThat(orderItemCount()).isEqualTo(1);
    assertThat(reload(fixture).getOnHand()).isEqualTo(10);
    assertThat(reload(fixture).getReserved()).isEqualTo(3);
    assertThat(movementRepository.findAll())
        .filteredOn(
            movement ->
                movement.getType()
                    == com.ivanfranchin.orderapi.inventory.InventoryMovementType.RESERVATION)
        .hasSize(1);
  }

  @Test
  void userDeletionWinningUserLockMakesOrderCreationFailWithoutSideEffects() throws Exception {
    Fixture fixture = fixture();
    userLockCoordinator.firstParticipant("delete");

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<String> delete =
          executor.submit(
              () ->
                  userLockCoordinator.asParticipant(
                      "delete",
                      () -> {
                        userService.deleteUser(fixture.user().getUsername(), "admin");
                        return "deleted";
                      }));
      assertThat(userLockCoordinator.awaitFirstLocked()).isTrue();
      Future<Object> create =
          executor.submit(
              () ->
                  userLockCoordinator.asParticipant(
                      "create",
                      () -> {
                        try {
                          return createOrder(fixture);
                        } catch (RuntimeException exception) {
                          return exception;
                        }
                      }));
      assertThat(userLockCoordinator.awaitSecondReached()).isTrue();
      assertThat(userLockCoordinator.transactionalCalls()).isEqualTo(2);
      userLockCoordinator.releaseFirst();

      assertThat(delete.get(10, TimeUnit.SECONDS)).isEqualTo("deleted");
      assertThat(create.get(10, TimeUnit.SECONDS)).isInstanceOf(UserNotFoundException.class);
    } finally {
      userLockCoordinator.releaseFirst();
    }

    assertThat(userRepository.findById(fixture.user().getId())).isEmpty();
    assertThat(orderRepository.count()).isZero();
    assertThat(orderItemCount()).isZero();
    assertThat(movementRepository.count()).isZero();
    assertThat(reload(fixture).getOnHand()).isEqualTo(10);
    assertThat(reload(fixture).getReserved()).isZero();
  }

  private Order createOrder(Fixture fixture) {
    return orderService.createOrder(
        new CreateOrderRequest(
            fixture.warehouse().getId(),
            List.of(new CreateOrderItemRequest(fixture.product().getId(), 3L))),
        fixture.user().getUsername());
  }

  private Fixture fixture() {
    saveUser("admin", Role.ADMIN);
    User user = saveUser("alice");
    Warehouse warehouse =
        warehouseRepository.saveAndFlush(
            new Warehouse("WH-" + System.nanoTime(), "Main", "Sydney"));
    Product product =
        productRepository.saveAndFlush(
            new Product("SKU-" + System.nanoTime(), "Product", new BigDecimal("5.00"), 1));
    Inventory inventory = inventoryRepository.saveAndFlush(new Inventory(product, warehouse, 10));
    return new Fixture(user, warehouse, product, inventory.getId());
  }

  private User saveUser(String username) {
    return saveUser(username, Role.USER);
  }

  private User saveUser(String username, Role role) {
    return userRepository.saveAndFlush(
        new User(
            username, "secret", username, username + System.nanoTime() + "@example.com", role));
  }

  private Inventory reload(Fixture fixture) {
    return inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
  }

  private long orderItemCount() {
    return jdbcTemplate.queryForObject("select count(*) from order_items", Long.class);
  }

  private record Fixture(User user, Warehouse warehouse, Product product, String inventoryId) {}

  @TestConfiguration
  @EnableAspectJAutoProxy
  static class LockTestConfiguration {
    @Bean
    UserLockCoordinator userLockCoordinator() {
      return new UserLockCoordinator();
    }

    @Bean
    UserLockAspect userLockAspect(UserLockCoordinator coordinator) {
      return new UserLockAspect(coordinator);
    }
  }

  static class UserLockCoordinator {
    private volatile boolean enabled;
    private volatile String firstParticipant;
    private volatile CountDownLatch firstLocked = new CountDownLatch(0);
    private volatile CountDownLatch secondReached = new CountDownLatch(0);
    private volatile CountDownLatch releaseFirst = new CountDownLatch(0);
    private final AtomicInteger transactionalCalls = new AtomicInteger();
    private final ThreadLocal<String> participant = new ThreadLocal<>();

    void firstParticipant(String identity) {
      transactionalCalls.set(0);
      firstParticipant = identity;
      firstLocked = new CountDownLatch(1);
      secondReached = new CountDownLatch(1);
      releaseFirst = new CountDownLatch(1);
      enabled = true;
    }

    <T> T asParticipant(String identity, Supplier<T> action) {
      participant.set(identity);
      try {
        return action.get();
      } finally {
        participant.remove();
      }
    }

    boolean awaitFirstLocked() throws InterruptedException {
      return firstLocked.await(5, TimeUnit.SECONDS);
    }

    boolean awaitSecondReached() throws InterruptedException {
      return secondReached.await(5, TimeUnit.SECONDS);
    }

    int transactionalCalls() {
      return transactionalCalls.get();
    }

    void releaseFirst() {
      releaseFirst.countDown();
    }

    void clear() {
      releaseFirst();
      enabled = false;
      firstParticipant = null;
      firstLocked = new CountDownLatch(0);
      secondReached = new CountDownLatch(0);
      releaseFirst = new CountDownLatch(0);
      transactionalCalls.set(0);
      participant.remove();
    }

    Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
      if (!enabled) return joinPoint.proceed();
      String identity = participant.get();
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        transactionalCalls.incrementAndGet();
      }
      if (firstParticipant.equals(identity)) {
        Object result = joinPoint.proceed();
        firstLocked.countDown();
        if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release first user lock holder");
        }
        return result;
      }
      secondReached.countDown();
      return joinPoint.proceed();
    }
  }

  @Aspect
  static class UserLockAspect {
    private final UserLockCoordinator coordinator;

    UserLockAspect(UserLockCoordinator coordinator) {
      this.coordinator = coordinator;
    }

    @Around(
        "execution(* com.ivanfranchin.orderapi.user.UserRepository.findByUsernameForUpdate(..))")
    Object synchronizeUserLock(ProceedingJoinPoint joinPoint) throws Throwable {
      return coordinator.intercept(joinPoint);
    }
  }
}
