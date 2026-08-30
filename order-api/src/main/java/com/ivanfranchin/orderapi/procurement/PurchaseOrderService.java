package com.ivanfranchin.orderapi.procurement;

import com.ivanfranchin.orderapi.inventory.Inventory;
import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.rest.dto.CreatePurchaseOrderRequest;
import com.ivanfranchin.orderapi.rest.dto.ReceivePurchaseOrderRequest;
import com.ivanfranchin.orderapi.supplier.Supplier;
import com.ivanfranchin.orderapi.supplier.SupplierRepository;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {
  private final PurchaseOrderRepository purchaseOrderRepository;
  private final GoodsReceiptRepository goodsReceiptRepository;
  private final SupplierRepository supplierRepository;
  private final WarehouseRepository warehouseRepository;
  private final ProductRepository productRepository;
  private final InventoryService inventoryService;
  private Clock clock = Clock.systemUTC();

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setClock(Clock c) {
    clock = c;
  }

  @Transactional(readOnly = true)
  public List<PurchaseOrder> findAll(
      String supplierId, String warehouseId, PurchaseOrderStatus status) {
    return purchaseOrderRepository.search(supplierId, warehouseId, status);
  }

  @Transactional(readOnly = true)
  public PurchaseOrder find(String id) {
    return purchaseOrderRepository
        .findDetailedById(id)
        .orElseThrow(
            () ->
                new PurchaseOrderNotFoundException(
                    "Purchase order with id %s not found".formatted(id)));
  }

  @Transactional
  public PurchaseOrder create(CreatePurchaseOrderRequest request) {
    Supplier supplier =
        supplierRepository
            .findById(request.supplierId())
            .orElseThrow(() -> new PurchaseOrderNotFoundException("Supplier not found"));
    Warehouse warehouse =
        warehouseRepository
            .findById(request.warehouseId())
            .orElseThrow(() -> new PurchaseOrderNotFoundException("Warehouse not found"));
    if (!supplier.isActive() || !warehouse.isActive())
      throw new InactiveProcurementReferenceException("Supplier and warehouse must be active");
    Set<String> seen = new HashSet<>();
    PurchaseOrder po = new PurchaseOrder(null, supplier, warehouse, request.expectedDeliveryDate());
    int line = 1;
    for (CreatePurchaseOrderRequest.Item item : request.items()) {
      if (!seen.add(item.productId()))
        throw new IllegalArgumentException("Duplicate product in purchase order");
      Product product =
          productRepository
              .findById(item.productId())
              .orElseThrow(() -> new PurchaseOrderNotFoundException("Product not found"));
      if (!product.isActive())
        throw new InactiveProcurementReferenceException("Product must be active");
      po.addItem(product, item.quantity(), item.unitCost(), line++);
    }
    return purchaseOrderRepository.saveAndFlush(po);
  }

  @Transactional
  public PurchaseOrder submit(String id) {
    PurchaseOrder po = locked(id);
    po.submit(now());
    return purchaseOrderRepository.saveAndFlush(po);
  }

  @Transactional
  public PurchaseOrder cancel(String id) {
    PurchaseOrder po = locked(id);
    po.cancel(now());
    return purchaseOrderRepository.saveAndFlush(po);
  }

  @Transactional
  public GoodsReceipt receive(String id, ReceivePurchaseOrderRequest request, String username) {
    PurchaseOrder po = locked(id);
    List<ResolvedReceiptItem> resolvedItems = resolveItems(request, po);
    String hash = hash(resolvedItems);
    Optional<GoodsReceipt> existing =
        goodsReceiptRepository.findByPurchaseOrderIdAndClientRequestId(
            id, request.clientRequestId());
    if (existing.isPresent()) {
      if (!existing.get().getPayloadHash().equals(hash))
        throw new DuplicateReceiptException(
            "clientRequestId was already used with a different payload");
      return existing.get();
    }
    if (po.getStatus() != PurchaseOrderStatus.SUBMITTED
        && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED)
      throw new InvalidPurchaseOrderStateException("Purchase order is not receivable");
    GoodsReceipt receipt = new GoodsReceipt(null, po, request.clientRequestId(), username, hash);
    goodsReceiptRepository.saveAndFlush(receipt);
    Set<String> seen = new HashSet<>();
    boolean complete = true;
    List<ResolvedReceiptItem> receiptItems =
        resolvedItems.stream()
            .sorted(Comparator.comparing(ri -> ri.item().getProduct().getId()))
            .toList();
    for (ResolvedReceiptItem resolved : receiptItems) {
      PurchaseOrderItem item = resolved.item();
      if (!seen.add(item.getId()))
        throw new IllegalArgumentException("Invalid or duplicate purchase order item");
      item.receive(resolved.quantity());
      Inventory inventory =
          inventoryService.receiveForPurchaseOrder(
              item.getProduct().getId(),
              po.getWarehouse().getId(),
              resolved.quantity(),
              receipt.getId(),
              username);
      long after = inventory.getOnHand();
      receipt.addItem(
          item,
          resolved.quantity(),
          Math.subtractExact(after, resolved.quantity()),
          after,
          item.getLineNumber());
    }
    for (PurchaseOrderItem item : po.getItems())
      if (item.getRemainingQuantity() > 0) complete = false;
    po.applyReceipt(complete, now());
    purchaseOrderRepository.saveAndFlush(po);
    return goodsReceiptRepository.saveAndFlush(receipt);
  }

  private PurchaseOrder locked(String id) {
    return purchaseOrderRepository
        .findByIdForUpdate(id)
        .orElseThrow(
            () ->
                new PurchaseOrderNotFoundException(
                    "Purchase order with id %s not found".formatted(id)));
  }

  private List<ResolvedReceiptItem> resolveItems(
      ReceivePurchaseOrderRequest request, PurchaseOrder po) {
    if (request.items() == null || request.items().isEmpty())
      throw new IllegalArgumentException("Receipt requires at least one item");
    Map<String, PurchaseOrderItem> byId =
        po.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getId, i -> i));
    Map<Integer, PurchaseOrderItem> byLine =
        po.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getLineNumber, i -> i));
    Set<String> seen = new HashSet<>();
    List<ResolvedReceiptItem> resolved = new java.util.ArrayList<>();
    for (ReceivePurchaseOrderRequest.Item requestItem : request.items()) {
      if (requestItem.purchaseOrderItemId() == null && requestItem.lineNumber() == null)
        throw new IllegalArgumentException("Receipt item must identify a purchase order item");
      PurchaseOrderItem item =
          requestItem.purchaseOrderItemId() != null
              ? byId.get(requestItem.purchaseOrderItemId())
              : byLine.get(requestItem.lineNumber());
      if (item == null) throw new IllegalArgumentException("Invalid purchase order item");
      if (requestItem.lineNumber() != null && item.getLineNumber() != requestItem.lineNumber())
        throw new IllegalArgumentException("Purchase order item id and line number do not match");
      if (!seen.add(item.getId()))
        throw new IllegalArgumentException("Invalid or duplicate purchase order item");
      resolved.add(new ResolvedReceiptItem(item, requestItem.quantity()));
    }
    return resolved;
  }

  private Instant now() {
    return com.ivanfranchin.orderapi.config.TimePrecision.databasePrecision(clock.instant());
  }

  private String hash(List<ResolvedReceiptItem> items) {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      String s =
          items.stream()
              .map(i -> i.item().getId() + ":" + i.quantity())
              .sorted()
              .collect(Collectors.joining("|"));
      byte[] b = d.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte x : b) out.append("%02x".formatted(x));
      return out.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record ResolvedReceiptItem(PurchaseOrderItem item, long quantity) {}
}
