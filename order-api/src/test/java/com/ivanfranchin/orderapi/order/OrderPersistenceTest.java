package com.ivanfranchin.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({
  OrderService.class,
  UserService.class,
  InventoryService.class,
  OrderExpirationProcessor.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderPersistenceTest {
  @Autowired private OrderService orderService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private WarehouseRepository warehouseRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryMovementRepository movementRepository;
  @Autowired private OrderExpirationProcessor orderExpirationProcessor;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Test
  @Transactional
  void lineNumbersPreserveRequestOrderAcrossReloadQueriesAndCancellation() {
    Fixture fixture = fixture(10, 10);
    CreateOrderRequest request =
        new CreateOrderRequest(
            fixture.warehouse().getId(),
            List.of(
                new CreateOrderItemRequest(fixture.third().getId(), 1L),
                new CreateOrderItemRequest(fixture.first().getId(), 2L),
                new CreateOrderItemRequest(fixture.second().getId(), 3L)));

    Order created = orderService.createOrder(request, fixture.user().getUsername());
    assertItemOrder(created, fixture.third(), fixture.first(), fixture.second());

    entityManager.clear();
    for (int attempt = 0; attempt < 3; attempt++) {
      Order reloaded = orderRepository.findDetailedById(created.getId()).orElseThrow();
      assertItemOrder(reloaded, fixture.third(), fixture.first(), fixture.second());
      entityManager.clear();
    }

    Order cancelled =
        orderService.cancelOrder(created.getId(), fixture.user().getUsername(), Role.USER);
    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertItemOrder(cancelled, fixture.third(), fixture.first(), fixture.second());
  }

  @Test
  void createsMultiItemOrderAndReservationMovementsAtomically() {
    Fixture fixture = fixture(10, 20);

    Order order = orderService.createOrder(request(fixture, 2, 3), fixture.user().getUsername());

    assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
    assertThat(order.getItems()).hasSize(2);
    assertThat(order.getTotalAmount()).isEqualByComparingTo("35.00");
    assertThat(reloadInventory(fixture.first()).getReserved()).isEqualTo(2);
    assertThat(reloadInventory(fixture.second()).getReserved()).isEqualTo(3);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RESERVATION)
        .hasSize(2)
        .allSatisfy(
            movement -> {
              assertThat(movement.getOnHandDelta()).isZero();
              assertThat(movement.getReservedDelta()).isPositive();
              assertThat(movement.getOnHandBefore()).isEqualTo(movement.getOnHandAfter());
              assertThat(movement.getReference()).isEqualTo(order.getId());
              assertThat(movement.getReason()).isEqualTo("Sales order reservation");
            });
  }

  @Test
  void laterProductPriceChangeDoesNotAlterSnapshot() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());

    Product changed = productRepository.findById(fixture.first().getId()).orElseThrow();
    changed.setPrice(new BigDecimal("99.00"));
    productRepository.saveAndFlush(changed);

    Order reloaded = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(reloaded.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("10.00");
    assertThat(reloaded.getItems().getFirst().getLineTotal()).isEqualByComparingTo("20.00");
  }

  @Test
  void insufficientLaterItemRollsBackEveryReservationOrderAndMovement() {
    Fixture fixture = fixture(10, 1);

    assertThatThrownBy(
            () -> orderService.createOrder(request(fixture, 2, 2), fixture.user().getUsername()))
        .isInstanceOf(InsufficientInventoryException.class);

    assertThat(orderRepository.count()).isZero();
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
    assertThat(reloadInventory(fixture.second()).getReserved()).isZero();
    assertThat(movementRepository.count()).isZero();
  }

  @Test
  void cancelReleasesAllReservationsAndCreatesReleaseMovements() {
    Fixture fixture = fixture(10, 20);
    Order order = orderService.createOrder(request(fixture, 2, 3), fixture.user().getUsername());

    Order cancelled =
        orderService.cancelOrder(order.getId(), fixture.user().getUsername(), Role.USER);

    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
    assertThat(reloadInventory(fixture.second()).getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .hasSize(2)
        .allSatisfy(
            movement -> {
              assertThat(movement.getOnHandDelta()).isZero();
              assertThat(movement.getReservedDelta()).isNegative();
              assertThat(movement.getReason()).isEqualTo("Sales order cancellation");
            });
  }

  @Test
  void repeatedCancellationIsIdempotentWithoutExtraMovements() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    orderService.cancelOrder(order.getId(), fixture.user().getUsername(), Role.USER);
    long movements = movementRepository.count();

    Order repeated =
        orderService.cancelOrder(order.getId(), fixture.user().getUsername(), Role.USER);

    assertThat(repeated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(movementRepository.count()).isEqualTo(movements);
  }

  @Test
  void paymentAndShipmentUpdateLifecycleAndInventoryAuditAtomically() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());

    Order paid = orderService.payOrder(order.getId(), fixture.user().getUsername(), Role.USER);
    Order shipped = orderService.shipOrder(order.getId(), "admin", Role.ADMIN);

    assertThat(paid.getPaidAt()).isNotNull();
    assertThat(paid.getPaymentReference()).startsWith("PAY-");
    assertThat(shipped.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    assertThat(shipped.getShippedAt()).isNotNull();
    Inventory inventory = reloadInventory(fixture.first());
    assertThat(inventory.getOnHand()).isEqualTo(8);
    assertThat(inventory.getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.SHIPMENT)
        .singleElement()
        .satisfies(
            movement -> {
              assertThat(movement.getOnHandDelta()).isEqualTo(-2);
              assertThat(movement.getReservedDelta()).isEqualTo(-2);
              assertThat(movement.getReference()).isEqualTo(order.getId());
              assertThat(movement.getReason()).isEqualTo("Sales order shipment");
            });
  }

  @Test
  void repeatedPaymentKeepsSameTimestampAndReferenceWithoutInventoryChanges() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    Order first = orderService.payOrder(order.getId(), fixture.user().getUsername(), Role.USER);
    Instant paidAt = first.getPaidAt();
    String reference = first.getPaymentReference();
    long movementCount = movementRepository.count();

    Order repeated = orderService.payOrder(order.getId(), fixture.user().getUsername(), Role.USER);

    assertThat(repeated.getPaidAt()).isEqualTo(paidAt);
    assertThat(repeated.getPaymentReference()).isEqualTo(reference);
    assertThat(movementRepository.count()).isEqualTo(movementCount);
    assertThat(reloadInventory(fixture.first()).getReserved()).isEqualTo(2);
  }

  @Test
  void expirationProcessorUsesItsTransactionToReleaseDueReservation() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    ReflectionTestUtils.setField(order, "expiresAt", Instant.now().minusSeconds(1));
    orderRepository.saveAndFlush(order);

    orderExpirationProcessor.process(order.getId());

    Order expired = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    assertThat(expired.getExpiredAt()).isNotNull();
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
    assertThat(reloadInventory(fixture.first()).getOnHand()).isEqualTo(10);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .singleElement()
        .satisfies(
            movement ->
                assertThat(movement.getReason()).isEqualTo("Sales order reservation expired"));
  }

  @Test
  void paymentAtOrAfterExpiryIsRejectedWithoutChangingOrder() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    ReflectionTestUtils.setField(order, "expiresAt", Instant.now());
    orderRepository.saveAndFlush(order);

    assertThatThrownBy(
            () -> orderService.payOrder(order.getId(), fixture.user().getUsername(), Role.USER))
        .isInstanceOf(OrderExpiredException.class);
    Order unchanged = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.RESERVED);
    assertThat(unchanged.getPaidAt()).isNull();
    assertThat(unchanged.getPaymentReference()).isNull();
  }

  @Test
  void repeatedShipmentIsIdempotentAfterReload() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    orderService.payOrder(order.getId(), fixture.user().getUsername(), Role.USER);

    Order shipped = orderService.shipOrder(order.getId(), "admin", Role.ADMIN);
    Instant shippedAt = shipped.getShippedAt();
    long movementCount =
        movementRepository.findAll().stream()
            .filter(movement -> movement.getType() == InventoryMovementType.SHIPMENT)
            .count();
    entityManager.clear();
    Order repeated = orderService.shipOrder(order.getId(), "admin", Role.ADMIN);
    entityManager.clear();

    Order reloaded = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(repeated.getShippedAt()).isEqualTo(shippedAt);
    assertThat(reloaded.getShippedAt()).isEqualTo(shippedAt);
    assertThat(reloadInventory(fixture.first()).getOnHand()).isEqualTo(8);
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
    assertThat(
            movementRepository.findAll().stream()
                .filter(movement -> movement.getType() == InventoryMovementType.SHIPMENT))
        .hasSize((int) movementCount);
  }

  @Test
  void shipmentStillWorksAfterProductAndWarehouseAreInactive() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    orderService.payOrder(order.getId(), fixture.user().getUsername(), Role.USER);
    Product product = productRepository.findById(fixture.first().getId()).orElseThrow();
    product.setActive(false);
    productRepository.saveAndFlush(product);
    Warehouse warehouse = warehouseRepository.findById(fixture.warehouse().getId()).orElseThrow();
    warehouse.setActive(false);
    warehouseRepository.saveAndFlush(warehouse);

    orderService.shipOrder(order.getId(), "admin", Role.ADMIN);
    entityManager.clear();

    assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.SHIPPED);
    assertThat(reloadInventory(fixture.first()).getOnHand()).isEqualTo(8);
  }

  @Test
  void multiItemShipmentRollsBackWhenLaterInventoryIsInvalid() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 2), fixture.user().getUsername());
    orderService.payOrder(order.getId(), fixture.user().getUsername(), Role.USER);
    Inventory second = reloadInventory(fixture.second());
    second.setReserved(0);
    inventoryRepository.saveAndFlush(second);

    assertThatThrownBy(() -> orderService.shipOrder(order.getId(), "admin", Role.ADMIN))
        .isInstanceOf(InsufficientInventoryException.class);
    entityManager.clear();

    assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.PAID);
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getShippedAt()).isNull();
    assertThat(reloadInventory(fixture.first()).getOnHand()).isEqualTo(10);
    assertThat(reloadInventory(fixture.first()).getReserved()).isEqualTo(2);
    assertThat(reloadInventory(fixture.second()).getOnHand()).isEqualTo(10);
    assertThat(reloadInventory(fixture.second()).getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.SHIPMENT)
        .isEmpty();
  }

  @Test
  void notDueReservedOrderIsSkipped() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());

    orderExpirationProcessor.process(order.getId());
    entityManager.clear();

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.RESERVED);
    assertThat(reloaded.getExpiredAt()).isNull();
    assertThat(reloadInventory(fixture.first()).getReserved()).isEqualTo(2);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .isEmpty();
  }

  @Test
  void terminalOrdersAreSkippedByExpirationProcessor() {
    Fixture fixture = fixture(10, 10);
    Order paid = orderService.createOrder(request(fixture, 1, 0), fixture.user().getUsername());
    orderService.payOrder(paid.getId(), fixture.user().getUsername(), Role.USER);
    Order shipped = orderService.createOrder(request(fixture, 1, 0), fixture.user().getUsername());
    orderService.payOrder(shipped.getId(), fixture.user().getUsername(), Role.USER);
    orderService.shipOrder(shipped.getId(), "admin", Role.ADMIN);
    Order cancelled =
        orderService.createOrder(request(fixture, 1, 0), fixture.user().getUsername());
    orderService.cancelOrder(cancelled.getId(), fixture.user().getUsername(), Role.USER);
    Order expired = orderService.createOrder(request(fixture, 1, 0), fixture.user().getUsername());
    ReflectionTestUtils.setField(expired, "expiresAt", Instant.now().minusSeconds(1));
    orderRepository.saveAndFlush(expired);
    orderExpirationProcessor.process(expired.getId());
    long releases =
        movementRepository.findAll().stream()
            .filter(movement -> movement.getType() == InventoryMovementType.RELEASE)
            .count();

    orderExpirationProcessor.process(paid.getId());
    orderExpirationProcessor.process(shipped.getId());
    orderExpirationProcessor.process(cancelled.getId());
    orderExpirationProcessor.process(expired.getId());

    assertThat(
            movementRepository.findAll().stream()
                .filter(movement -> movement.getType() == InventoryMovementType.RELEASE))
        .hasSize((int) releases);
  }

  @Test
  void orderExpiresExactlyAtExpiresAtUsingInjectedClock() {
    Instant now = Instant.parse("2026-02-01T00:00:00Z");
    orderService.setClock(java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    ReflectionTestUtils.setField(order, "expiresAt", now);
    orderRepository.saveAndFlush(order);

    orderExpirationProcessor.process(order.getId());
    entityManager.clear();

    Order expired = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    assertThat(expired.getExpiredAt()).isEqualTo(now);
    assertThat(reloadInventory(fixture.first()).getOnHand()).isEqualTo(10);
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
  }

  @Test
  void inactiveProductAndWarehouseDoNotBlockExpiration() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    Product product = productRepository.findById(fixture.first().getId()).orElseThrow();
    product.setActive(false);
    productRepository.saveAndFlush(product);
    Warehouse warehouse = warehouseRepository.findById(fixture.warehouse().getId()).orElseThrow();
    warehouse.setActive(false);
    warehouseRepository.saveAndFlush(warehouse);
    ReflectionTestUtils.setField(order, "expiresAt", Instant.now().minusSeconds(1));
    orderRepository.saveAndFlush(order);

    orderExpirationProcessor.process(order.getId());

    assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.EXPIRED);
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
  }

  @Test
  void multiItemExpirationRollsBackWhenLaterInventoryIsInvalid() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 2), fixture.user().getUsername());
    Inventory second = reloadInventory(fixture.second());
    second.setReserved(0);
    inventoryRepository.saveAndFlush(second);
    ReflectionTestUtils.setField(order, "expiresAt", Instant.now().minusSeconds(1));
    orderRepository.saveAndFlush(order);

    assertThatThrownBy(() -> orderExpirationProcessor.process(order.getId()))
        .isInstanceOf(InsufficientInventoryException.class);
    entityManager.clear();
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.RESERVED);
    assertThat(reloadInventory(fixture.first()).getReserved()).isEqualTo(2);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .isEmpty();
  }

  @Test
  void expirationMovementHasExactAuditValuesAndReason() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    ReflectionTestUtils.setField(order, "expiresAt", Instant.now().minusSeconds(1));
    orderRepository.saveAndFlush(order);

    orderExpirationProcessor.process(order.getId());
    entityManager.clear();

    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .singleElement()
        .satisfies(
            movement -> {
              assertThat(movement.getOnHandDelta()).isZero();
              assertThat(movement.getReservedDelta()).isEqualTo(-2);
              assertThat(movement.getOnHandBefore()).isEqualTo(10);
              assertThat(movement.getOnHandAfter()).isEqualTo(10);
              assertThat(movement.getReservedBefore()).isEqualTo(2);
              assertThat(movement.getReservedAfter()).isZero();
              assertThat(movement.getReference()).isEqualTo(order.getId());
              assertThat(movement.getReason()).isEqualTo("Sales order reservation expired");
            });
  }

  @Test
  void expirationProcessorRunsInRequiresNewTransaction() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    ReflectionTestUtils.setField(order, "expiresAt", Instant.now().minusSeconds(1));
    orderRepository.saveAndFlush(order);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              orderExpirationProcessor.process(order.getId());
              status.setRollbackOnly();
            });
    entityManager.clear();

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    assertThat(reloaded.getExpiredAt()).isNotNull();
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .hasSize(1);
  }

  @Test
  void cancellationStillReleasesReservationsAfterProductAndWarehouseAreDeactivated() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 2, 0), fixture.user().getUsername());
    Product product = productRepository.findById(fixture.first().getId()).orElseThrow();
    product.setActive(false);
    productRepository.saveAndFlush(product);
    Warehouse warehouse = warehouseRepository.findById(fixture.warehouse().getId()).orElseThrow();
    warehouse.setActive(false);
    warehouseRepository.saveAndFlush(warehouse);

    Order cancelled =
        orderService.cancelOrder(order.getId(), fixture.user().getUsername(), Role.USER);

    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(reloadInventory(fixture.first()).getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getType() == InventoryMovementType.RELEASE)
        .hasSize(1);
  }

  @Test
  void databasePreventsDuplicateProductsWithinOrder() {
    Fixture fixture = fixture(10, 10);
    Order order =
        new Order(
            "direct-order",
            fixture.user(),
            fixture.warehouse(),
            java.time.Instant.now().plusSeconds(1800));
    order.addItem(fixture.first(), 1, 1);
    order.addItem(fixture.first(), 1, 2);

    assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databasePreventsDuplicateLineNumbersWithinOrder() {
    Fixture fixture = fixture(10, 10);
    Order order =
        new Order(
            "duplicate-line-order",
            fixture.user(),
            fixture.warehouse(),
            java.time.Instant.now().plusSeconds(1800));
    order.addItem(fixture.first(), 1, 1);
    order.addItem(fixture.second(), 1, 1);

    assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsNonpositiveLineNumber() {
    Fixture fixture = fixture(10, 10);
    Order order = orderService.createOrder(request(fixture, 1, 0), fixture.user().getUsername());

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into order_items"
                        + " (id, order_id, product_id, quantity, line_number, unit_price, line_total)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                    "invalid-line-item",
                    order.getId(),
                    fixture.second().getId(),
                    1L,
                    0,
                    new BigDecimal("5.00"),
                    new BigDecimal("5.00")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private Fixture fixture(long firstStock, long secondStock) {
    User user =
        userRepository.saveAndFlush(
            new User(
                "user-" + System.nanoTime(),
                "secret",
                "Test User",
                System.nanoTime() + "@example.com",
                Role.USER));
    Warehouse warehouse =
        warehouseRepository.saveAndFlush(
            new Warehouse("WH-" + System.nanoTime(), "Main", "Sydney"));
    Product first =
        productRepository.saveAndFlush(
            new Product("SKU-A-" + System.nanoTime(), "A", new BigDecimal("10.00"), 1));
    Product second =
        productRepository.saveAndFlush(
            new Product("SKU-B-" + System.nanoTime(), "B", new BigDecimal("5.00"), 1));
    Product third =
        productRepository.saveAndFlush(
            new Product("SKU-C-" + System.nanoTime(), "C", new BigDecimal("3.00"), 1));
    inventoryRepository.saveAndFlush(new Inventory(first, warehouse, firstStock));
    inventoryRepository.saveAndFlush(new Inventory(second, warehouse, secondStock));
    inventoryRepository.saveAndFlush(new Inventory(third, warehouse, 10));
    return new Fixture(user, warehouse, first, second, third);
  }

  private CreateOrderRequest request(Fixture fixture, long firstQuantity, long secondQuantity) {
    java.util.ArrayList<CreateOrderItemRequest> items = new java.util.ArrayList<>();
    if (firstQuantity > 0)
      items.add(new CreateOrderItemRequest(fixture.first().getId(), firstQuantity));
    if (secondQuantity > 0)
      items.add(new CreateOrderItemRequest(fixture.second().getId(), secondQuantity));
    return new CreateOrderRequest(fixture.warehouse().getId(), items);
  }

  private Inventory reloadInventory(Product product) {
    return inventoryRepository
        .findByProductIdAndWarehouseId(product.getId(), currentWarehouseId(product))
        .orElseThrow();
  }

  private String currentWarehouseId(Product product) {
    return inventoryRepository.findAll().stream()
        .filter(inventory -> inventory.getProduct().getId().equals(product.getId()))
        .findFirst()
        .orElseThrow()
        .getWarehouse()
        .getId();
  }

  private void assertItemOrder(Order order, Product... products) {
    assertThat(order.getItems()).extracting(OrderItem::getLineNumber).containsExactly(1, 2, 3);
    assertThat(order.getItems())
        .extracting(item -> item.getProduct().getId())
        .containsExactly(
            java.util.Arrays.stream(products).map(Product::getId).toArray(String[]::new));
  }

  private record Fixture(
      User user, Warehouse warehouse, Product first, Product second, Product third) {}
}
