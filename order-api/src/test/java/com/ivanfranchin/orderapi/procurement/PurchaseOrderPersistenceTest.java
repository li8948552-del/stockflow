package com.ivanfranchin.orderapi.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ivanfranchin.orderapi.inventory.InvalidInventoryQuantityException;
import com.ivanfranchin.orderapi.inventory.InventoryMovementRepository;
import com.ivanfranchin.orderapi.inventory.InventoryMovementType;
import com.ivanfranchin.orderapi.inventory.InventoryRepository;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.rest.dto.CreatePurchaseOrderRequest;
import com.ivanfranchin.orderapi.rest.dto.ReceivePurchaseOrderRequest;
import com.ivanfranchin.orderapi.supplier.Supplier;
import com.ivanfranchin.orderapi.supplier.SupplierRepository;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({PurchaseOrderService.class, com.ivanfranchin.orderapi.inventory.InventoryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PurchaseOrderPersistenceTest {
  @Autowired private PurchaseOrderService service;
  @Autowired private PurchaseOrderRepository orderRepository;
  @Autowired private SupplierRepository supplierRepository;
  @Autowired private WarehouseRepository warehouseRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @MockitoSpyBean private InventoryMovementRepository movementRepository;
  @Autowired private GoodsReceiptRepository receiptRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void createAndReloadsDraftWithStableItemsAndTotals() {
    Fixture f = fixture();
    PurchaseOrder order = service.create(request(f, 2, 3));
    entityManager.clear();
    PurchaseOrder reloaded = orderRepository.findDetailedById(order.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
    assertThat(reloaded.getItems())
        .extracting(PurchaseOrderItem::getLineNumber)
        .containsExactly(1, 2);
    assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("20.00");
    assertThat(reloaded.getItems())
        .extracting(PurchaseOrderItem::getLineTotal)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("5.00"), new BigDecimal("15.00"));
    assertThat(reloaded.getVersion()).isNotNull();
  }

  @Test
  void submitAndReloadPreservesStateAndTimestamp() {
    Fixture f = fixture();
    PurchaseOrder order = service.create(request(f, 1, 0));
    PurchaseOrder submitted = service.submit(order.getId());
    InstantHolder first = new InstantHolder(submitted.getSubmittedAt());
    PurchaseOrder repeated = service.submit(order.getId());
    assertThat(repeated.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
    assertThat(repeated.getSubmittedAt()).isEqualTo(first.value);
  }

  @Test
  void cancelDraftIsIdempotentAfterReload() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 1, 0));
    assertThat(service.cancel(o.getId()).getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    assertThat(service.cancel(o.getId()).getCancelledAt()).isNotNull();
  }

  @Test
  void cancelSubmittedIsIdempotentAfterReload() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 1, 0));
    service.submit(o.getId());
    assertThat(service.cancel(o.getId()).getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    assertThat(service.cancel(o.getId()).getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
  }

  @Test
  void firstReceiptCreatesInventoryAndExactAuditRecords() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    GoodsReceipt r = service.receive(o.getId(), receipt(o, 2), "admin");
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(i -> assertThat(i.getOnHand()).isEqualTo(2));
    assertThat(movementRepository.findAll())
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.getType()).isEqualTo(InventoryMovementType.RECEIPT);
              assertThat(m.getReference()).isEqualTo(r.getId());
            });
    assertThat(orderRepository.findDetailedById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.RECEIVED);
  }

  @Test
  void receiptAddsToExistingInventoryWithoutChangingReserved() {
    Fixture f = fixture();
    inventoryRepository.saveAndFlush(
        new com.ivanfranchin.orderapi.inventory.Inventory(f.product1(), f.warehouse(), 5));
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    service.receive(o.getId(), receipt(o, 2), "admin");
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.getOnHand()).isEqualTo(7);
              assertThat(i.getReserved()).isZero();
            });
  }

  @Test
  void partialThenFinalReceiptUpdatesQuantitiesAndStates() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 3, 0));
    service.submit(o.getId());
    service.receive(o.getId(), receipt(o, 1, "r1"), "admin");
    assertThat(orderRepository.findDetailedById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    service.receive(o.getId(), receipt(o, 2, "r2"), "admin");
    assertThat(orderRepository.findDetailedById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.RECEIVED);
  }

  @Test
  void repeatedClientRequestIdReturnsSameReceiptWithoutSideEffects() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    ReceivePurchaseOrderRequest r = receipt(o, 2, "same");
    GoodsReceipt first = service.receive(o.getId(), r, "admin");
    entityManager.clear();
    GoodsReceipt second = service.receive(o.getId(), r, "admin");
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(receiptRepository.count()).isOne();
    assertThat(movementRepository.count()).isOne();
  }

  @Test
  void sameClientRequestIdWithDifferentPayloadConflictsWithoutSideEffects() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 3, 0));
    service.submit(o.getId());
    service.receive(o.getId(), receipt(o, 1, "same"), "admin");
    assertThatThrownBy(() -> service.receive(o.getId(), receipt(o, 2, "same"), "admin"))
        .isInstanceOf(DuplicateReceiptException.class);
    assertThat(receiptRepository.count()).isOne();
  }

  @Test
  void sameClientRequestIdAcrossIdAndLineNumberIsIdempotent() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    GoodsReceipt first = service.receive(o.getId(), receipt(o, 2, "cross"), "admin");
    entityManager.clear();
    GoodsReceipt second = service.receive(o.getId(), receiptByLine(o, 2, "cross"), "admin");
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(receiptRepository.count()).isOne();
    assertThat(movementRepository.count()).isOne();
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(i -> assertThat(i.getOnHand()).isEqualTo(2));
  }

  @Test
  void reverseRepresentationRetryIsIdempotent() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    GoodsReceipt first = service.receive(o.getId(), receiptByLine(o, 2, "reverse"), "admin");
    entityManager.clear();
    GoodsReceipt second = service.receive(o.getId(), receipt(o, 2, "reverse"), "admin");
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(orderRepository.findDetailedById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.RECEIVED);
  }

  @Test
  void bothIdentifiersForSameItemAreAcceptedAndCanonical() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    PurchaseOrderItem item = o.getItems().getFirst();
    ReceivePurchaseOrderRequest both =
        new ReceivePurchaseOrderRequest(
            "both",
            List.of(new ReceivePurchaseOrderRequest.Item(item.getId(), item.getLineNumber(), 2L)));
    GoodsReceipt first = service.receive(o.getId(), both, "admin");
    entityManager.clear();
    GoodsReceipt second = service.receive(o.getId(), receipt(o, 2, "both"), "admin");
    assertThat(second.getId()).isEqualTo(first.getId());
  }

  @Test
  void conflictingIdAndLineNumberAreRejectedWithoutSideEffects() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 2));
    service.submit(o.getId());
    PurchaseOrderItem first = o.getItems().getFirst();
    PurchaseOrderItem second = o.getItems().get(1);
    ReceivePurchaseOrderRequest conflicting =
        new ReceivePurchaseOrderRequest(
            "conflict",
            List.of(
                new ReceivePurchaseOrderRequest.Item(first.getId(), second.getLineNumber(), 1L)));
    assertThatThrownBy(() -> service.receive(o.getId(), conflicting, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(receiptRepository.count()).isZero();
    assertThat(inventoryRepository.count()).isZero();
    assertThat(movementRepository.count()).isZero();
  }

  @Test
  void mixedRepresentationDuplicateItemIsRejected() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    PurchaseOrderItem item = o.getItems().getFirst();
    ReceivePurchaseOrderRequest duplicate =
        new ReceivePurchaseOrderRequest(
            "mixed-duplicate",
            List.of(
                new ReceivePurchaseOrderRequest.Item(item.getId(), null, 1L),
                new ReceivePurchaseOrderRequest.Item(null, item.getLineNumber(), 1L)));
    assertThatThrownBy(() -> service.receive(o.getId(), duplicate, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
    entityManager.clear();
    assertThat(receiptRepository.count()).isZero();
    assertThat(movementRepository.count()).isZero();
    assertThat(inventoryRepository.count()).isZero();
    assertThat(
            orderRepository
                .findDetailedById(o.getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getReceivedQuantity())
        .isZero();
  }

  @Test
  void repeatedClientRequestIdAfterReloadHasNoSideEffects() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    ReceivePurchaseOrderRequest r = receipt(o, 2, "reload-key");
    GoodsReceipt first = service.receive(o.getId(), r, "admin");
    entityManager.clear();
    long inventory = inventoryRepository.findAll().getFirst().getOnHand();
    long received =
        orderRepository
            .findDetailedById(o.getId())
            .orElseThrow()
            .getItems()
            .getFirst()
            .getReceivedQuantity();
    long receipts = receiptRepository.count(),
        receiptItems =
            entityManager
                .createQuery("select count(i) from GoodsReceiptItem i", Long.class)
                .getSingleResult();
    long movements = movementRepository.count();
    GoodsReceipt second = service.receive(o.getId(), r, "admin");
    entityManager.clear();
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(inventoryRepository.findAll().getFirst().getOnHand()).isEqualTo(inventory);
    assertThat(
            orderRepository
                .findDetailedById(o.getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getReceivedQuantity())
        .isEqualTo(received);
    assertThat(receiptRepository.count()).isEqualTo(receipts);
    assertThat(
            entityManager
                .createQuery("select count(i) from GoodsReceiptItem i", Long.class)
                .getSingleResult())
        .isEqualTo(receiptItems);
    assertThat(movementRepository.count()).isEqualTo(movements);
  }

  @Test
  void completedOrderRetryReturnsOriginalReceipt() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    ReceivePurchaseOrderRequest r = receipt(o, 2, "completed-key");
    GoodsReceipt first = service.receive(o.getId(), r, "admin");
    entityManager.clear();
    GoodsReceipt retry = service.receive(o.getId(), r, "admin");
    assertThat(retry.getId()).isEqualTo(first.getId());
    assertThat(orderRepository.findDetailedById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.RECEIVED);
    assertThat(receiptRepository.count()).isOne();
  }

  @Test
  void reorderedEquivalentPayloadIsIdempotent() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 2));
    service.submit(o.getId());
    ReceivePurchaseOrderRequest firstRequest = mixedReceiptBoth(o, "order-key", false);
    GoodsReceipt first = service.receive(o.getId(), firstRequest, "admin");
    entityManager.clear();
    ReceivePurchaseOrderRequest reordered = mixedReceiptBoth(o, "order-key", true);
    GoodsReceipt second = service.receive(o.getId(), reordered, "admin");
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(receiptRepository.count()).isOne();
    assertThat(movementRepository.count()).isEqualTo(2);
  }

  @Test
  void multiItemReceiptUpdatesEveryInventoryAndAuditRow() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 3));
    service.submit(o.getId());
    service.receive(o.getId(), receiptBoth(o, "multi", 2, 3), "admin");
    entityManager.clear();
    assertThat(inventoryRepository.count()).isEqualTo(2);
    assertThat(movementRepository.count()).isEqualTo(2);
    assertThat(
            entityManager
                .createQuery("select count(i) from GoodsReceiptItem i", Long.class)
                .getSingleResult())
        .isEqualTo(2L);
    assertThat(orderRepository.findDetailedById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.RECEIVED);
  }

  @Test
  void partialReceiptThenCancellationKeepsAuditAndInventory() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 3, 0));
    service.submit(o.getId());
    service.receive(o.getId(), receipt(o, 1, "partial"), "admin");
    service.cancel(o.getId());
    entityManager.clear();
    assertThat(orderRepository.findDetailedById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseOrderStatus.CANCELLED);
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(i -> assertThat(i.getOnHand()).isEqualTo(1));
    assertThat(receiptRepository.count()).isOne();
    assertThat(movementRepository.findAll())
        .singleElement()
        .satisfies(m -> assertThat(m.getType()).isEqualTo(InventoryMovementType.RECEIPT));
    assertThatThrownBy(() -> service.receive(o.getId(), receipt(o, 1, "after-cancel"), "admin"))
        .isInstanceOf(InvalidPurchaseOrderStateException.class);
  }

  @Test
  void overReceiptRollsBackEverything() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 2, 0));
    service.submit(o.getId());
    assertThatThrownBy(() -> service.receive(o.getId(), receipt(o, 3, "over"), "admin"))
        .isInstanceOf(OverReceiptException.class);
    entityManager.clear();
    PurchaseOrder reloaded = orderRepository.findDetailedById(o.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
    assertThat(reloaded.getItems().getFirst().getReceivedQuantity()).isZero();
    assertThat(receiptRepository.count()).isZero();
    assertThat(movementRepository.count()).isZero();
    assertThat(inventoryRepository.count()).isZero();
  }

  @Test
  void inventoryOverflowRollsBackReceipt() {
    Fixture f = fixture();
    inventoryRepository.saveAndFlush(
        new com.ivanfranchin.orderapi.inventory.Inventory(
            f.product1(), f.warehouse(), Long.MAX_VALUE));
    PurchaseOrder o = service.create(request(f, 1, 0));
    service.submit(o.getId());
    assertThatThrownBy(() -> service.receive(o.getId(), receipt(o, 1, "overflow"), "admin"))
        .isInstanceOf(InvalidInventoryQuantityException.class);
    entityManager.clear();
    assertThat(
            orderRepository
                .findDetailedById(o.getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getReceivedQuantity())
        .isZero();
    assertThat(receiptRepository.count()).isZero();
    assertThat(movementRepository.count()).isZero();
    assertThat(inventoryRepository.findAll())
        .singleElement()
        .satisfies(i -> assertThat(i.getOnHand()).isEqualTo(Long.MAX_VALUE));
  }

  @Test
  void movementSaveFailureRollsBackReceipt() {
    Fixture f = fixture();
    PurchaseOrder o = service.create(request(f, 1, 0));
    service.submit(o.getId());
    doThrow(new RuntimeException("simulated movement persistence failure"))
        .when(movementRepository)
        .saveAndFlush(any());
    assertThatThrownBy(() -> service.receive(o.getId(), receipt(o, 1, "movement-failure"), "admin"))
        .isInstanceOf(RuntimeException.class);
    entityManager.clear();
    assertThat(receiptRepository.count()).isZero();
    assertThat(movementRepository.count()).isZero();
    assertThat(
            orderRepository
                .findDetailedById(o.getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getReceivedQuantity())
        .isZero();
  }

  private ReceivePurchaseOrderRequest receipt(PurchaseOrder o, long quantity) {
    return receipt(o, quantity, "key-" + quantity);
  }

  private ReceivePurchaseOrderRequest receipt(PurchaseOrder o, long quantity, String key) {
    return new ReceivePurchaseOrderRequest(
        key,
        List.of(
            new ReceivePurchaseOrderRequest.Item(o.getItems().getFirst().getId(), null, quantity)));
  }

  private ReceivePurchaseOrderRequest receiptByLine(PurchaseOrder o, long quantity, String key) {
    return new ReceivePurchaseOrderRequest(
        key,
        List.of(
            new ReceivePurchaseOrderRequest.Item(
                null, o.getItems().getFirst().getLineNumber(), quantity)));
  }

  private ReceivePurchaseOrderRequest receiptBoth(PurchaseOrder o, String key, long q1, long q2) {
    return receiptBothOrdered(o, key, q1, q2, false);
  }

  private ReceivePurchaseOrderRequest mixedReceiptBoth(
      PurchaseOrder o, String key, boolean reverse) {
    ReceivePurchaseOrderRequest.Item first =
        new ReceivePurchaseOrderRequest.Item(o.getItems().get(0).getId(), null, 1L);
    ReceivePurchaseOrderRequest.Item second =
        new ReceivePurchaseOrderRequest.Item(null, o.getItems().get(1).getLineNumber(), 1L);
    if (!reverse) return new ReceivePurchaseOrderRequest(key, List.of(first, second));
    return new ReceivePurchaseOrderRequest(
        key,
        List.of(
            new ReceivePurchaseOrderRequest.Item(o.getItems().get(1).getId(), null, 1L),
            new ReceivePurchaseOrderRequest.Item(null, o.getItems().get(0).getLineNumber(), 1L)));
  }

  private ReceivePurchaseOrderRequest receiptBothOrdered(
      PurchaseOrder o, String key, long q1, long q2, boolean reverse) {
    ReceivePurchaseOrderRequest.Item first =
        new ReceivePurchaseOrderRequest.Item(o.getItems().get(0).getId(), null, q1);
    ReceivePurchaseOrderRequest.Item second =
        new ReceivePurchaseOrderRequest.Item(o.getItems().get(1).getId(), null, q2);
    return new ReceivePurchaseOrderRequest(
        key, reverse ? List.of(second, first) : List.of(first, second));
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

  private Fixture fixture() {
    Supplier s =
        supplierRepository.saveAndFlush(new Supplier("SUP-PERSIST", "Supplier", null, null, 2));
    Warehouse w = warehouseRepository.saveAndFlush(new Warehouse("WH-PERSIST", "Main", "Sydney"));
    Product p1 = productRepository.saveAndFlush(new Product("SKU-P1", "One", BigDecimal.ONE, 1));
    Product p2 = productRepository.saveAndFlush(new Product("SKU-P2", "Two", BigDecimal.ONE, 1));
    return new Fixture(s, w, p1, p2);
  }

  private record Fixture(
      Supplier supplier, Warehouse warehouse, Product product1, Product product2) {}

  private record InstantHolder(java.time.Instant value) {}
}
