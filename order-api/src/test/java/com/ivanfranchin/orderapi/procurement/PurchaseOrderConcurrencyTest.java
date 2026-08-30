package com.ivanfranchin.orderapi.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ivanfranchin.orderapi.inventory.Inventory;
import com.ivanfranchin.orderapi.inventory.InventoryMovementRepository;
import com.ivanfranchin.orderapi.inventory.InventoryMovementType;
import com.ivanfranchin.orderapi.inventory.InventoryRepository;
import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.order.Order;
import com.ivanfranchin.orderapi.order.OrderRepository;
import com.ivanfranchin.orderapi.order.OrderService;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderItemRequest;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderRequest;
import com.ivanfranchin.orderapi.rest.dto.CreatePurchaseOrderRequest;
import com.ivanfranchin.orderapi.rest.dto.ReceivePurchaseOrderRequest;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.supplier.Supplier;
import com.ivanfranchin.orderapi.supplier.SupplierRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Import({
  PurchaseOrderService.class,
  InventoryService.class,
  OrderService.class,
  UserService.class,
  PurchaseOrderConcurrencyTest.LockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PurchaseOrderConcurrencyTest {
  @Autowired private PurchaseOrderService service;
  @Autowired private OrderService orderService;
  @Autowired private InventoryService inventoryService;
  @Autowired private SupplierRepository supplierRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private WarehouseRepository warehouseRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private PurchaseOrderRepository orderRepository;
  @Autowired private OrderRepository salesOrderRepository;
  @Autowired private GoodsReceiptRepository receiptRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryMovementRepository movementRepository;

  @Autowired
  @Qualifier("purchaseOrderLockCoordinator") private PurchaseOrderLockCoordinator coordinator;

  @Autowired
  @Qualifier("warehouseLockCoordinator") private WarehouseLockCoordinator warehouseCoordinator;

  @AfterEach
  void reset() {
    coordinator.clear();
    warehouseCoordinator.clear();
  }

  @Test
  void concurrentSameClientRequestIdCreatesOneReceipt() throws Exception {
    Fixture f = fixture(10, 0);
    PurchaseOrder po = submitted(f, 2, 0);
    ReceivePurchaseOrderRequest request = receipt(po, 2, "same");
    ReceivePurchaseOrderRequest lineRequest =
        new ReceivePurchaseOrderRequest(
            "same",
            List.of(
                new ReceivePurchaseOrderRequest.Item(
                    null, po.getItems().getFirst().getLineNumber(), 2L)));
    List<Object> results =
        concurrently(
            () -> service.receive(po.getId(), request, "admin"),
            () -> service.receive(po.getId(), lineRequest, "admin"));
    assertThat(results).allMatch(GoodsReceipt.class::isInstance);
    assertThat(((GoodsReceipt) results.get(0)).getId())
        .isEqualTo(((GoodsReceipt) results.get(1)).getId());
    assertThat(receiptRepository.count()).isOne();
    assertThat(movementRepository.count()).isOne();
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(i -> assertThat(i.getOnHand()).isEqualTo(12));
  }

  @Test
  void concurrentDifferentKeysCannotOverReceive() throws Exception {
    Fixture f = fixture(0, 0);
    PurchaseOrder po = submitted(f, 5, 0);
    List<Object> results =
        concurrently(
            () -> service.receive(po.getId(), receipt(po, 5, "a"), "admin"),
            () -> service.receive(po.getId(), receipt(po, 5, "b"), "admin"));
    assertThat(results).filteredOn(GoodsReceipt.class::isInstance).hasSize(1);
    assertThat(results).filteredOn(t -> t instanceof RuntimeException).hasSize(1);
    assertThat(receiptRepository.count()).isOne();
    assertThat(
            orderRepository
                .findDetailedById(po.getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getReceivedQuantity())
        .isEqualTo(5);
  }

  @Test
  void concurrentLegalPartialReceiptsPreserveBoth() throws Exception {
    Fixture f = fixture(0, 0);
    PurchaseOrder po = submitted(f, 10, 0);
    List<Object> results =
        concurrently(
            () -> service.receive(po.getId(), receipt(po, 4, "a"), "admin"),
            () -> service.receive(po.getId(), receipt(po, 6, "b"), "admin"));
    assertThat(results).allMatch(GoodsReceipt.class::isInstance);
    assertThat(
            orderRepository
                .findDetailedById(po.getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getReceivedQuantity())
        .isEqualTo(10);
    assertThat(receiptRepository.count()).isEqualTo(2);
    assertThat(movementRepository.count()).isEqualTo(2);
  }

  @Test
  void receiptWinsBeforeCancellation() {
    Fixture f = fixture(0, 0);
    PurchaseOrder po = submitted(f, 2, 0);
    service.receive(po.getId(), receipt(po, 2, "receipt-first"), "admin");
    assertThatThrownBy(() -> service.cancel(po.getId()))
        .isInstanceOf(InvalidPurchaseOrderStateException.class);
    assertThat(orderRepository.findDetailedById(po.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.RECEIVED);
  }

  @Test
  void cancellationWinsBeforeReceipt() {
    Fixture f = fixture(0, 0);
    PurchaseOrder po = submitted(f, 2, 0);
    service.cancel(po.getId());
    assertThatThrownBy(() -> service.receive(po.getId(), receipt(po, 2, "cancel-first"), "admin"))
        .isInstanceOf(InvalidPurchaseOrderStateException.class);
    assertThat(receiptRepository.count()).isZero();
    assertThat(movementRepository.count()).isZero();
  }

  @Test
  void procurementAndManualReceiptPreserveBothDeltas() throws Exception {
    Fixture f = fixture(0, 0);
    PurchaseOrder po = submitted(f, 2, 0);
    service.receive(po.getId(), receipt(po, 2, "purchase"), "admin");
    inventoryService.receive(f.product1().getId(), f.warehouse().getId(), 3, "manual", "admin");
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(i -> assertThat(i.getOnHand()).isEqualTo(5));
    assertThat(movementRepository.findAll()).hasSize(2);
  }

  @Test
  void procurementReceiptAndSalesReservationPreserveBoth() throws Exception {
    Fixture f = fixture(5, 0);
    User user =
        userRepository.saveAndFlush(
            new User("buyer", "secret", "Buyer", "buyer@example.com", Role.USER));
    PurchaseOrder po = submitted(f, 2, 0);
    warehouseCoordinator.block(2);
    CreateOrderRequest sales =
        new CreateOrderRequest(
            f.warehouse().getId(), List.of(new CreateOrderItemRequest(f.product1().getId(), 3L)));
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<GoodsReceipt> receipt =
          executor.submit(() -> service.receive(po.getId(), receipt(po, 2, "purchase"), "admin"));
      Future<Order> order =
          executor.submit(() -> orderService.createOrder(sales, user.getUsername()));
      assertThat(warehouseCoordinator.await(5)).isTrue();
      warehouseCoordinator.release();
      assertThat(receipt.get(10, TimeUnit.SECONDS)).isNotNull();
      assertThat(order.get(10, TimeUnit.SECONDS).getStatus().name()).isEqualTo("RESERVED");
    } finally {
      warehouseCoordinator.release();
    }
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.getOnHand()).isEqualTo(7);
              assertThat(i.getReserved()).isEqualTo(3);
              assertThat(i.getAvailable()).isEqualTo(4);
            });
    assertThat(salesOrderRepository.count()).isOne();
    assertThat(receiptRepository.count()).isOne();
    assertThat(movementRepository.findAll())
        .extracting("type")
        .containsExactlyInAnyOrder(
            InventoryMovementType.RECEIPT, InventoryMovementType.RESERVATION);
  }

  @Test
  void oppositeProductInputOrderDoesNotDeadlock() throws Exception {
    Fixture f = fixture(10, 10);
    PurchaseOrder po = submitted(f, 2, 2);
    List<Object> results =
        concurrently(
            () -> service.receive(po.getId(), receiptBoth(po, "forward", false), "admin"),
            () -> service.receive(po.getId(), receiptBoth(po, "reverse", true), "admin"));
    assertThat(results).allMatch(t -> t instanceof GoodsReceipt || t instanceof RuntimeException);
    assertThat(receiptRepository.count()).isGreaterThanOrEqualTo(1);
  }

  private List<Object> concurrently(Throwing first, Throwing second) throws Exception {
    coordinator.block(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Object> a = executor.submit(() -> call(first));
      Future<Object> b = executor.submit(() -> call(second));
      assertThat(coordinator.await(5)).isTrue();
      coordinator.release();
      return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
    } finally {
      coordinator.release();
    }
  }

  private Object call(Throwing action) {
    try {
      return action.run();
    } catch (RuntimeException e) {
      return e;
    }
  }

  private interface Throwing {
    Object run();
  }

  private PurchaseOrder submitted(Fixture f, long q1, long q2) {
    PurchaseOrder po = service.create(request(f, q1, q2));
    service.submit(po.getId());
    return po;
  }

  private ReceivePurchaseOrderRequest receipt(PurchaseOrder po, long q, String key) {
    return new ReceivePurchaseOrderRequest(
        key,
        List.of(new ReceivePurchaseOrderRequest.Item(po.getItems().getFirst().getId(), null, q)));
  }

  private ReceivePurchaseOrderRequest receiptBoth(PurchaseOrder po, String key, boolean reverse) {
    ReceivePurchaseOrderRequest.Item a =
        new ReceivePurchaseOrderRequest.Item(po.getItems().get(0).getId(), null, 1L);
    ReceivePurchaseOrderRequest.Item b =
        new ReceivePurchaseOrderRequest.Item(po.getItems().get(1).getId(), null, 1L);
    return new ReceivePurchaseOrderRequest(key, reverse ? List.of(b, a) : List.of(a, b));
  }

  private CreatePurchaseOrderRequest request(Fixture f, long q1, long q2) {
    java.util.ArrayList<CreatePurchaseOrderRequest.Item> items =
        new java.util.ArrayList<>(
            List.of(
                new CreatePurchaseOrderRequest.Item(
                    f.product1().getId(), q1, new BigDecimal("2.50"))));
    if (q2 > 0)
      items.add(
          new CreatePurchaseOrderRequest.Item(f.product2().getId(), q2, new BigDecimal("5.00")));
    return new CreatePurchaseOrderRequest(f.supplier().getId(), f.warehouse().getId(), null, items);
  }

  private Fixture fixture(long stock1, long stock2) {
    Supplier s =
        supplierRepository.saveAndFlush(new Supplier("SUP-CONC", "Supplier", null, null, 2));
    Warehouse w = warehouseRepository.saveAndFlush(new Warehouse("WH-CONC", "Main", "Sydney"));
    Product p1 = productRepository.saveAndFlush(new Product("SKU-C1", "One", BigDecimal.ONE, 1));
    Product p2 = productRepository.saveAndFlush(new Product("SKU-C2", "Two", BigDecimal.ONE, 1));
    if (stock1 > 0) inventoryRepository.saveAndFlush(new Inventory(p1, w, stock1));
    if (stock2 > 0) inventoryRepository.saveAndFlush(new Inventory(p2, w, stock2));
    return new Fixture(s, w, p1, p2);
  }

  private record Fixture(
      Supplier supplier, Warehouse warehouse, Product product1, Product product2) {}

  @TestConfiguration
  @EnableAspectJAutoProxy
  static class LockConfig {
    @Bean
    PurchaseOrderLockCoordinator purchaseOrderLockCoordinator() {
      return new PurchaseOrderLockCoordinator();
    }

    @Bean
    PurchaseOrderLockAspect purchaseOrderLockAspect(
        @Qualifier("purchaseOrderLockCoordinator") PurchaseOrderLockCoordinator c) {
      return new PurchaseOrderLockAspect(c);
    }

    @Bean
    WarehouseLockCoordinator warehouseLockCoordinator() {
      return new WarehouseLockCoordinator();
    }

    @Bean
    WarehouseLockAspect warehouseLockAspect(WarehouseLockCoordinator c) {
      return new WarehouseLockAspect(c);
    }
  }

  static class PurchaseOrderLockCoordinator {
    private volatile CountDownLatch reached = new CountDownLatch(0),
        release = new CountDownLatch(0);
    private volatile boolean enabled;

    void block(int n) {
      reached = new CountDownLatch(n);
      release = new CountDownLatch(1);
      enabled = true;
    }

    boolean await(int seconds) throws InterruptedException {
      return reached.await(seconds, TimeUnit.SECONDS);
    }

    void release() {
      release.countDown();
    }

    void clear() {
      enabled = false;
      release();
      reached = new CountDownLatch(0);
      release = new CountDownLatch(0);
    }

    Object intercept(ProceedingJoinPoint p) throws Throwable {
      if (!enabled) return p.proceed();
      if (TransactionSynchronizationManager.isActualTransactionActive()) reached.countDown();
      if (!release.await(5, TimeUnit.SECONDS))
        throw new IllegalStateException("lock coordination timeout");
      return p.proceed();
    }
  }

  @Aspect
  static class PurchaseOrderLockAspect {
    private final PurchaseOrderLockCoordinator coordinator;

    PurchaseOrderLockAspect(PurchaseOrderLockCoordinator c) {
      coordinator = c;
    }

    @Around(
        "execution(* com.ivanfranchin.orderapi.procurement.PurchaseOrderRepository.findByIdForUpdate(..))")
    Object lock(ProceedingJoinPoint p) throws Throwable {
      return coordinator.intercept(p);
    }
  }

  static class WarehouseLockCoordinator extends PurchaseOrderLockCoordinator {}

  @Aspect
  static class WarehouseLockAspect {
    private final WarehouseLockCoordinator coordinator;

    WarehouseLockAspect(WarehouseLockCoordinator coordinator) {
      this.coordinator = coordinator;
    }

    @Around(
        "execution(* com.ivanfranchin.orderapi.warehouse.WarehouseRepository.findByIdForUpdate(..))")
    Object lock(ProceedingJoinPoint p) throws Throwable {
      return coordinator.intercept(p);
    }
  }
}
