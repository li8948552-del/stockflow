package com.ivanfranchin.orderapi.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.RollbackException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Import({InventoryService.class, InventoryPersistenceTest.LockTestConfiguration.class})
class InventoryPersistenceTest {

  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryMovementRepository movementRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private WarehouseRepository warehouseRepository;
  @Autowired private WarehouseLockTestCoordinator warehouseLockTestCoordinator;
  @Autowired private EntityManager entityManager;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private InventoryService inventoryService;

  @AfterEach
  void clearWarehouseLockSynchronization() {
    warehouseLockTestCoordinator.clear();
  }

  @Test
  void persistsLargeQuantitiesAndCalculatedAvailable() {
    References references = references("LARGE");
    long onHand = (long) Integer.MAX_VALUE + 10_000L;
    Inventory inventory =
        inventoryRepository.saveAndFlush(
            new Inventory(references.product(), references.warehouse(), onHand));
    inventory.setReserved(5_000);
    inventoryRepository.saveAndFlush(inventory);
    entityManager.clear();

    Inventory reloaded = inventoryRepository.findById(inventory.getId()).orElseThrow();
    assertThat(reloaded.getOnHand()).isEqualTo(onHand);
    assertThat(reloaded.getAvailable()).isEqualTo(onHand - 5_000);
  }

  @Test
  void receiptRepositoriesDeclarePessimisticWriteLocks() throws Exception {
    assertThat(
            WarehouseRepository.class
                .getMethod("findByIdForUpdate", String.class)
                .getAnnotation(Lock.class)
                .value())
        .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    assertThat(
            ProductRepository.class
                .getMethod("findByIdForUpdate", String.class)
                .getAnnotation(Lock.class)
                .value())
        .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void databaseUniqueConstraintRejectsDuplicateProductWarehouse() {
    References references = references("UNIQUE");
    inventoryRepository.saveAndFlush(
        new Inventory(references.product(), references.warehouse(), 10));

    assertThatThrownBy(
            () ->
                inventoryRepository.saveAndFlush(
                    new Inventory(references.product(), references.warehouse(), 20)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void movementQueryUsesStableIdDescendingTieBreaker() {
    References references = references("MOVEMENT");
    Inventory inventory =
        inventoryRepository.saveAndFlush(
            new Inventory(references.product(), references.warehouse(), 10));
    InventoryMovement older =
        InventoryMovement.create(
            inventory, InventoryMovementType.INITIAL_STOCK, 10, 0, 10, 0, 0, null, null, "admin");
    InventoryMovement newer =
        InventoryMovement.create(
            inventory, InventoryMovementType.RECEIPT, 5, 10, 15, 0, 0, null, null, "admin");
    Instant sameCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
    ReflectionTestUtils.setField(older, "id", "movement-a");
    ReflectionTestUtils.setField(newer, "id", "movement-b");
    ReflectionTestUtils.setField(older, "createdAt", sameCreatedAt);
    ReflectionTestUtils.setField(newer, "createdAt", sameCreatedAt);
    movementRepository.saveAndFlush(older);
    movementRepository.saveAndFlush(newer);

    for (int attempt = 0; attempt < 3; attempt++) {
      assertThat(
              movementRepository.findDetailedByInventoryIdOrderByCreatedAtDescIdDesc(
                  inventory.getId()))
          .extracting(InventoryMovement::getId)
          .containsExactly("movement-b", "movement-a");
    }
  }

  @Test
  void lowStockQueryUsesAvailableAndProductReorderPoint() {
    References low = references("LOW");
    References healthy = references("HEALTHY");
    Inventory lowInventory =
        inventoryRepository.saveAndFlush(new Inventory(low.product(), low.warehouse(), 10));
    lowInventory.setReserved(1);
    inventoryRepository.saveAndFlush(lowInventory);
    inventoryRepository.saveAndFlush(new Inventory(healthy.product(), healthy.warehouse(), 11));

    assertThat(inventoryRepository.findInventory(null, null, true))
        .extracting(Inventory::getId)
        .containsExactly(lowInventory.getId());
  }

  @Test
  void movementPersistenceNormalizesUnicodeTextAndSupportsSupplementaryLimits() {
    References references = references("AUDIT-TEXT");
    Inventory inventory =
        inventoryRepository.saveAndFlush(
            new Inventory(references.product(), references.warehouse(), 10));
    String reason = "📦".repeat(InventoryMovement.REASON_MAX_LENGTH);
    String reference = "📦".repeat(InventoryMovement.REFERENCE_MAX_LENGTH);
    InventoryMovement movement =
        InventoryMovement.create(
            inventory,
            InventoryMovementType.ADJUSTMENT_IN,
            1,
            10,
            11,
            0,
            0,
            "\u00a0" + reference + "\u2003",
            "\u2003" + reason + "\u00a0",
            "AdminUser");

    InventoryMovement saved = movementRepository.saveAndFlush(movement);
    entityManager.clear();

    InventoryMovement reloaded = movementRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getReference()).isEqualTo(reference);
    assertThat(reloaded.getReason()).isEqualTo(reason);
    assertThat(reloaded.getCreatedBy()).isEqualTo("AdminUser");
  }

  @Test
  void directPersistenceRejectsUnicodeBlankAdjustmentReason() {
    References references = references("AUDIT-BLANK");
    Inventory inventory =
        inventoryRepository.saveAndFlush(
            new Inventory(references.product(), references.warehouse(), 10));
    InventoryMovement movement =
        InventoryMovement.create(
            inventory,
            InventoryMovementType.ADJUSTMENT_OUT,
            -1,
            10,
            9,
            0,
            0,
            null,
            "valid",
            "admin");
    ReflectionTestUtils.setField(movement, "reason", "\u00a0\u2003");

    assertThatThrownBy(() -> movementRepository.saveAndFlush(movement))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void directPersistenceRejectsAuditTextOverCodePointLimit() {
    References references = references("AUDIT-LONG");
    Inventory inventory =
        inventoryRepository.saveAndFlush(
            new Inventory(references.product(), references.warehouse(), 10));
    InventoryMovement movement =
        InventoryMovement.create(
            inventory,
            InventoryMovementType.ADJUSTMENT_IN,
            1,
            10,
            11,
            0,
            0,
            null,
            "valid",
            "admin");
    ReflectionTestUtils.setField(
        movement, "reason", "📦".repeat(InventoryMovement.REASON_MAX_LENGTH + 1));

    assertThatThrownBy(() -> movementRepository.saveAndFlush(movement))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void concurrentInitialReceiptsBothSucceedWithoutLosingStock() throws Exception {
    EntityManager setup = entityManagerFactory.createEntityManager();
    setup.getTransaction().begin();
    Product product = new Product("SKU-RACE", "Keyboard", BigDecimal.ONE, 10);
    Warehouse warehouse = new Warehouse("WH-RACE", "Main", "Sydney");
    setup.persist(product);
    setup.persist(warehouse);
    setup.getTransaction().commit();
    setup.close();

    runConcurrentReceipts(
        () -> inventoryService.receive(product.getId(), warehouse.getId(), 10, null, "admin"),
        () -> inventoryService.receive(product.getId(), warehouse.getId(), 10, null, "admin"));

    List<Inventory> inventories =
        inventoryRepository.findAll().stream()
            .filter(
                inventory ->
                    inventory.getProduct().getId().equals(product.getId())
                        && inventory.getWarehouse().getId().equals(warehouse.getId()))
            .toList();
    assertThat(inventories).hasSize(1);
    assertThat(inventories.getFirst().getOnHand()).isEqualTo(20);
    assertThat(inventories.getFirst().getReserved()).isZero();
    assertThat(movementRepository.findAll())
        .filteredOn(
            movement -> movement.getInventory().getId().equals(inventories.getFirst().getId()))
        .extracting(InventoryMovement::getType)
        .containsExactlyInAnyOrder(
            InventoryMovementType.INITIAL_STOCK, InventoryMovementType.RECEIPT);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void concurrentReceiptsForExistingInventoryDoNotLoseStock() throws Exception {
    EntityManager setup = entityManagerFactory.createEntityManager();
    setup.getTransaction().begin();
    Product product = new Product("SKU-RACE-EXISTING", "Keyboard", BigDecimal.ONE, 10);
    Warehouse warehouse = new Warehouse("WH-RACE-EXISTING", "Main", "Sydney");
    setup.persist(product);
    setup.persist(warehouse);
    setup.persist(new Inventory(product, warehouse, 5));
    setup.getTransaction().commit();
    setup.close();

    runConcurrentReceipts(
        () -> inventoryService.receive(product.getId(), warehouse.getId(), 10, null, "admin"),
        () -> inventoryService.receive(product.getId(), warehouse.getId(), 10, null, "admin"));

    Inventory result =
        inventoryRepository
            .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
            .orElseThrow();
    assertThat(result.getOnHand()).isEqualTo(25);
    assertThat(movementRepository.findAll())
        .filteredOn(movement -> movement.getInventory().getId().equals(result.getId()))
        .extracting(InventoryMovement::getType)
        .containsExactlyInAnyOrder(InventoryMovementType.RECEIPT, InventoryMovementType.RECEIPT);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void concurrentReceiptsForDifferentWarehousesRemainIndependent() throws Exception {
    EntityManager setup = entityManagerFactory.createEntityManager();
    setup.getTransaction().begin();
    Product product = new Product("SKU-RACE-SEPARATE", "Keyboard", BigDecimal.ONE, 10);
    Warehouse firstWarehouse = new Warehouse("WH-RACE-A", "First", "Sydney");
    Warehouse secondWarehouse = new Warehouse("WH-RACE-B", "Second", "Melbourne");
    setup.persist(product);
    setup.persist(firstWarehouse);
    setup.persist(secondWarehouse);
    setup.getTransaction().commit();
    setup.close();

    runConcurrentReceipts(
        () -> inventoryService.receive(product.getId(), firstWarehouse.getId(), 10, null, "admin"),
        () ->
            inventoryService.receive(product.getId(), secondWarehouse.getId(), 20, null, "admin"));

    assertThat(
            inventoryRepository
                .findByProductIdAndWarehouseId(product.getId(), firstWarehouse.getId())
                .orElseThrow()
                .getOnHand())
        .isEqualTo(10);
    assertThat(
            inventoryRepository
                .findByProductIdAndWarehouseId(product.getId(), secondWarehouse.getId())
                .orElseThrow()
                .getOnHand())
        .isEqualTo(20);
    assertThat(movementRepository.findAll())
        .extracting(InventoryMovement::getType)
        .containsExactlyInAnyOrder(
            InventoryMovementType.INITIAL_STOCK, InventoryMovementType.INITIAL_STOCK);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void receiptRejectsProductDeactivatedWhileWaitingForWarehouseLock() throws Exception {
    EntityManager setup = entityManagerFactory.createEntityManager();
    setup.getTransaction().begin();
    Product product = new Product("SKU-INACTIVE-RACE", "Keyboard", BigDecimal.ONE, 10);
    Warehouse warehouse = new Warehouse("WH-INACTIVE-RACE", "Main", "Sydney");
    setup.persist(product);
    setup.persist(warehouse);
    setup.getTransaction().commit();
    setup.close();

    EntityManager warehouseLocker = entityManagerFactory.createEntityManager();
    warehouseLocker.getTransaction().begin();
    warehouseLocker.find(Warehouse.class, warehouse.getId(), LockModeType.PESSIMISTIC_WRITE);

    warehouseLockTestCoordinator.signalOnly(1);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Inventory> receipt =
        executor.submit(
            () -> inventoryService.receive(product.getId(), warehouse.getId(), 10, null, "admin"));
    try {
      assertThat(warehouseLockTestCoordinator.awaitReached()).isTrue();

      EntityManager deactivator = entityManagerFactory.createEntityManager();
      deactivator.getTransaction().begin();
      deactivator.find(Product.class, product.getId()).setActive(false);
      deactivator.getTransaction().commit();
      deactivator.close();

      warehouseLocker.getTransaction().commit();
      warehouseLocker.close();

      assertThatThrownBy(() -> receipt.get(10, TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class)
          .hasCauseInstanceOf(InactiveInventoryReferenceException.class)
          .hasMessageContaining("Product");
    } finally {
      if (warehouseLocker.isOpen()) {
        if (warehouseLocker.getTransaction().isActive()) {
          warehouseLocker.getTransaction().rollback();
        }
        warehouseLocker.close();
      }
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(
            inventoryRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()))
        .isEmpty();
    assertThat(movementRepository.findAll()).isEmpty();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void versionRejectsLostUpdate() {
    String suffix = "LOCK";
    EntityManager setup = entityManagerFactory.createEntityManager();
    setup.getTransaction().begin();
    Product product = new Product("SKU-" + suffix, "Keyboard", BigDecimal.ONE, 10);
    Warehouse warehouse = new Warehouse("WH-" + suffix, "Main", "Sydney");
    setup.persist(product);
    setup.persist(warehouse);
    Inventory inventory = new Inventory(product, warehouse, 10);
    setup.persist(inventory);
    setup.getTransaction().commit();
    setup.close();

    EntityManager first = entityManagerFactory.createEntityManager();
    EntityManager second = entityManagerFactory.createEntityManager();
    first.getTransaction().begin();
    second.getTransaction().begin();
    Inventory firstCopy = first.find(Inventory.class, inventory.getId());
    Inventory staleCopy = second.find(Inventory.class, inventory.getId());
    firstCopy.setOnHand(11);
    first.getTransaction().commit();
    staleCopy.setOnHand(12);

    assertThatThrownBy(() -> second.getTransaction().commit())
        .isInstanceOf(RollbackException.class)
        .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
    first.close();
    second.close();
  }

  private References references(String suffix) {
    Product product =
        productRepository.saveAndFlush(
            new Product("SKU-" + suffix, "Keyboard", BigDecimal.ONE, 10));
    Warehouse warehouse =
        warehouseRepository.saveAndFlush(new Warehouse("WH-" + suffix, "Main", "Sydney"));
    return new References(product, warehouse);
  }

  private void runConcurrentReceipts(Callable<Inventory> first, Callable<Inventory> second)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    warehouseLockTestCoordinator.blockUntilReleased(2);
    Callable<Inventory> synchronizedFirst =
        () -> {
          start.await();
          return first.call();
        };
    Callable<Inventory> synchronizedSecond =
        () -> {
          start.await();
          return second.call();
        };
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Inventory>> results =
          List.of(executor.submit(synchronizedFirst), executor.submit(synchronizedSecond));
      start.countDown();
      assertThat(warehouseLockTestCoordinator.awaitReached()).isTrue();
      assertThat(warehouseLockTestCoordinator.transactionalCalls()).isEqualTo(2);
      warehouseLockTestCoordinator.release();
      for (Future<Inventory> result : results) {
        assertThat(result.get(10, TimeUnit.SECONDS)).isNotNull();
      }
    } finally {
      warehouseLockTestCoordinator.release();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @TestConfiguration
  @EnableAspectJAutoProxy
  static class LockTestConfiguration {

    @Bean
    WarehouseLockTestCoordinator warehouseLockTestCoordinator() {
      return new WarehouseLockTestCoordinator();
    }

    @Bean
    WarehouseLockTestAspect warehouseLockTestAspect(
        WarehouseLockTestCoordinator warehouseLockTestCoordinator) {
      return new WarehouseLockTestAspect(warehouseLockTestCoordinator);
    }
  }

  static class WarehouseLockTestCoordinator {

    private volatile boolean enabled;
    private volatile CountDownLatch reached = new CountDownLatch(0);
    private volatile CountDownLatch release = new CountDownLatch(0);
    private volatile CountDownLatch unlockedInventoryQueriesCompleted = new CountDownLatch(0);
    private final AtomicInteger transactionalCalls = new AtomicInteger();

    void blockUntilReleased(int expectedCalls) {
      configure(expectedCalls, true);
    }

    void signalOnly(int expectedCalls) {
      configure(expectedCalls, false);
    }

    private void configure(int expectedCalls, boolean block) {
      transactionalCalls.set(0);
      reached = new CountDownLatch(expectedCalls);
      release = new CountDownLatch(block ? 1 : 0);
      unlockedInventoryQueriesCompleted = new CountDownLatch(expectedCalls);
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
      unlockedInventoryQueriesCompleted = new CountDownLatch(0);
      transactionalCalls.set(0);
    }

    Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
      if (!enabled) {
        return joinPoint.proceed();
      }
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        transactionalCalls.incrementAndGet();
      }
      reached.countDown();
      if (!release.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to release warehouse lock queries");
      }
      return joinPoint.proceed();
    }

    Object interceptInventoryQuery(ProceedingJoinPoint joinPoint) throws Throwable {
      Object result = joinPoint.proceed();
      if (!enabled || warehousePessimisticWriteLockConfigured()) {
        return result;
      }
      unlockedInventoryQueriesCompleted.countDown();
      if (!unlockedInventoryQueriesCompleted.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out synchronizing unlocked inventory queries");
      }
      return result;
    }

    private boolean warehousePessimisticWriteLockConfigured() throws NoSuchMethodException {
      Lock lock =
          WarehouseRepository.class
              .getMethod("findByIdForUpdate", String.class)
              .getAnnotation(Lock.class);
      return lock != null && lock.value() == LockModeType.PESSIMISTIC_WRITE;
    }
  }

  @Aspect
  static class WarehouseLockTestAspect {

    private final WarehouseLockTestCoordinator coordinator;

    WarehouseLockTestAspect(WarehouseLockTestCoordinator coordinator) {
      this.coordinator = coordinator;
    }

    @Around(
        "execution(* com.ivanfranchin.orderapi.warehouse.WarehouseRepository.findByIdForUpdate(..))")
    Object synchronizeWarehouseLockQuery(ProceedingJoinPoint joinPoint) throws Throwable {
      return coordinator.intercept(joinPoint);
    }

    @Around(
        "execution(* com.ivanfranchin.orderapi.inventory.InventoryRepository.findByProductIdAndWarehouseId(..))")
    Object synchronizeInventoryQueryWhenWarehouseLockIsMissing(ProceedingJoinPoint joinPoint)
        throws Throwable {
      return coordinator.interceptInventoryQuery(joinPoint);
    }
  }

  private record References(Product product, Warehouse warehouse) {}
}
