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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({OrderService.class, UserService.class, InventoryService.class})
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
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

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
