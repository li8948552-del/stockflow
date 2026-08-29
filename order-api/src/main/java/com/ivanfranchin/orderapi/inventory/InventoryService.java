package com.ivanfranchin.orderapi.inventory;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductNotFoundException;
import com.ivanfranchin.orderapi.product.ProductRepository;
import com.ivanfranchin.orderapi.validation.BusinessText;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseNotFoundException;
import com.ivanfranchin.orderapi.warehouse.WarehouseRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InventoryService {

  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository movementRepository;
  private final ProductRepository productRepository;
  private final WarehouseRepository warehouseRepository;

  @Transactional(readOnly = true)
  public List<Inventory> getInventory(String productId, String warehouseId, boolean lowStock) {
    return inventoryRepository.findInventory(productId, warehouseId, lowStock);
  }

  @Transactional(readOnly = true)
  public Inventory getInventory(String id) {
    return inventoryRepository
        .findDetailedById(id)
        .orElseThrow(
            () -> new InventoryNotFoundException("Inventory with id %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public List<InventoryMovement> getMovements(String inventoryId) {
    getInventory(inventoryId);
    return movementRepository.findDetailedByInventoryIdOrderByCreatedAtDescIdDesc(inventoryId);
  }

  @Transactional
  public Inventory receive(
      String productId, String warehouseId, long quantity, String reference, String createdBy) {
    if (quantity <= 0) {
      throw new InvalidInventoryQuantityException("Receipt quantity must be greater than zero");
    }
    String normalizedReference =
        normalizeOptionalAuditField(reference, "reference", InventoryMovement.REFERENCE_MAX_LENGTH);
    validateCreatedBy(createdBy);
    // Keep this lock order consistent across receipts to avoid deadlocks.
    Warehouse warehouse = getWarehouseForUpdate(warehouseId);
    Product product = getProductForUpdate(productId);
    validateActiveReferences(product, warehouse);

    return inventoryRepository
        .findByProductIdAndWarehouseId(productId, warehouseId)
        .map(inventory -> receiveExisting(inventory, quantity, normalizedReference, createdBy))
        .orElseGet(
            () -> createInitial(product, warehouse, quantity, normalizedReference, createdBy));
  }

  @Transactional
  public Inventory adjust(
      String inventoryId, long quantityDelta, String reason, String reference, String createdBy) {
    if (quantityDelta == 0) {
      throw new InvalidInventoryQuantityException("Adjustment quantity must not be zero");
    }
    String normalizedReason =
        normalizeRequiredAuditField(reason, "reason", InventoryMovement.REASON_MAX_LENGTH);
    String normalizedReference =
        normalizeOptionalAuditField(reference, "reference", InventoryMovement.REFERENCE_MAX_LENGTH);
    validateCreatedBy(createdBy);
    Inventory inventory = getInventory(inventoryId);
    long before = inventory.getOnHand();
    long after = addExact(before, quantityDelta);
    if (after < 0) {
      throw new InsufficientInventoryException("Adjustment would make on-hand quantity negative");
    }
    if (after < inventory.getReserved()) {
      throw new InsufficientInventoryException(
          "Adjustment would make on-hand quantity lower than reserved quantity");
    }
    inventory.setOnHand(after);
    Inventory saved = saveInventory(inventory);
    saveMovement(
        saved,
        quantityDelta > 0
            ? InventoryMovementType.ADJUSTMENT_IN
            : InventoryMovementType.ADJUSTMENT_OUT,
        quantityDelta,
        0,
        before,
        after,
        saved.getReserved(),
        saved.getReserved(),
        normalizedReference,
        normalizedReason,
        createdBy);
    return saved;
  }

  private Inventory createInitial(
      Product product, Warehouse warehouse, long quantity, String reference, String createdBy) {
    Inventory inventory = new Inventory(product, warehouse, quantity);
    Inventory saved = saveInventory(inventory);
    saveMovement(
        saved,
        InventoryMovementType.INITIAL_STOCK,
        quantity,
        0,
        0,
        quantity,
        0,
        0,
        reference,
        null,
        createdBy);
    return saved;
  }

  private Inventory receiveExisting(
      Inventory inventory, long quantity, String reference, String createdBy) {
    long before = inventory.getOnHand();
    long after = addExact(before, quantity);
    inventory.setOnHand(after);
    Inventory saved = saveInventory(inventory);
    saveMovement(
        saved,
        InventoryMovementType.RECEIPT,
        quantity,
        0,
        before,
        after,
        saved.getReserved(),
        saved.getReserved(),
        reference,
        null,
        createdBy);
    return saved;
  }

  private Product getProductForUpdate(String id) {
    return productRepository
        .findByIdForUpdate(id)
        .orElseThrow(
            () -> new ProductNotFoundException("Product with id %s not found".formatted(id)));
  }

  private Warehouse getWarehouseForUpdate(String id) {
    return warehouseRepository
        .findByIdForUpdate(id)
        .orElseThrow(
            () -> new WarehouseNotFoundException("Warehouse with id %s not found".formatted(id)));
  }

  private void validateActiveReferences(Product product, Warehouse warehouse) {
    if (!warehouse.isActive()) {
      throw new InactiveInventoryReferenceException(
          "Warehouse with id %s is inactive".formatted(warehouse.getId()));
    }
    if (!product.isActive()) {
      throw new InactiveInventoryReferenceException(
          "Product with id %s is inactive".formatted(product.getId()));
    }
  }

  private Inventory saveInventory(Inventory inventory) {
    try {
      return inventoryRepository.saveAndFlush(inventory);
    } catch (DataIntegrityViolationException exception) {
      if (isProductWarehouseConstraint(exception)) {
        throw new InventoryConflictException(
            "Inventory already exists for product %s and warehouse %s"
                .formatted(inventory.getProduct().getId(), inventory.getWarehouse().getId()));
      }
      throw exception;
    } catch (OptimisticLockingFailureException exception) {
      throw new InventoryOptimisticLockException(
          "Inventory was updated by another transaction", exception);
    }
  }

  private void saveMovement(
      Inventory inventory,
      InventoryMovementType type,
      long onHandDelta,
      long reservedDelta,
      long onHandBefore,
      long onHandAfter,
      long reservedBefore,
      long reservedAfter,
      String reference,
      String reason,
      String createdBy) {
    movementRepository.saveAndFlush(
        InventoryMovement.create(
            inventory,
            type,
            onHandDelta,
            reservedDelta,
            onHandBefore,
            onHandAfter,
            reservedBefore,
            reservedAfter,
            reference,
            reason,
            createdBy));
  }

  /** Joins the caller's order transaction and follows Warehouse, Product, Inventory lock order. */
  @Transactional(propagation = Propagation.MANDATORY)
  public ReservationBatch reserveForOrder(
      String warehouseId, Map<String, Long> requested, String orderId, String createdBy) {
    validateCreatedBy(createdBy);
    Warehouse warehouse = getWarehouseForUpdate(warehouseId);
    if (!warehouse.isActive()) {
      throw new InactiveInventoryReferenceException(
          "Warehouse with id %s is inactive".formatted(warehouseId));
    }
    List<String> productIds = requested.keySet().stream().sorted().toList();
    List<Product> products = new ArrayList<>();
    for (String productId : productIds) {
      Product product = getProductForUpdate(productId);
      if (!product.isActive()) {
        throw new InactiveInventoryReferenceException(
            "Product with id %s is inactive".formatted(productId));
      }
      products.add(product);
    }
    List<Inventory> inventories = new ArrayList<>();
    for (String productId : productIds) {
      inventories.add(getInventoryForUpdate(productId, warehouseId));
    }
    List<ReservedProduct> results = new ArrayList<>();
    for (int index = 0; index < productIds.size(); index++) {
      Product product = products.get(index);
      Inventory inventory = inventories.get(index);
      long quantity = requested.get(product.getId());
      long before = inventory.getReserved();
      long after = addExact(before, quantity);
      if (after > inventory.getOnHand()) {
        throw new InsufficientInventoryException(
            "Insufficient available inventory for product %s".formatted(product.getId()));
      }
      inventory.setReserved(after);
      Inventory saved = saveInventory(inventory);
      saveMovement(
          saved,
          InventoryMovementType.RESERVATION,
          0,
          quantity,
          saved.getOnHand(),
          saved.getOnHand(),
          before,
          after,
          orderId,
          "Sales order reservation",
          createdBy);
      results.add(new ReservedProduct(product, quantity));
    }
    return new ReservationBatch(warehouse, List.copyOf(results));
  }

  /** Joins the caller's cancellation transaction and uses the same global lock order. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void releaseForOrder(
      String warehouseId, Map<String, Long> quantities, String orderId, String createdBy) {
    releaseForOrder(warehouseId, quantities, orderId, createdBy, "Sales order cancellation");
  }

  /** Joins the caller's transaction and releases reservations with an explicit audit reason. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void releaseForOrder(
      String warehouseId,
      Map<String, Long> quantities,
      String orderId,
      String createdBy,
      String reason) {
    validateCreatedBy(createdBy);
    getWarehouseForUpdate(warehouseId);
    List<String> productIds = quantities.keySet().stream().sorted().toList();
    for (String productId : productIds) getProductForUpdate(productId);
    List<Inventory> inventories = new ArrayList<>();
    for (String productId : productIds) {
      inventories.add(getInventoryForUpdate(productId, warehouseId));
    }
    for (Inventory inventory : inventories) {
      long quantity = quantities.get(inventory.getProduct().getId());
      long before = inventory.getReserved();
      if (quantity > before) {
        throw new InsufficientInventoryException("Reservation release exceeds reserved quantity");
      }
      long after = before - quantity;
      inventory.setReserved(after);
      Inventory saved = saveInventory(inventory);
      saveMovement(
          saved,
          InventoryMovementType.RELEASE,
          0,
          -quantity,
          saved.getOnHand(),
          saved.getOnHand(),
          before,
          after,
          orderId,
          reason,
          createdBy);
    }
  }

  /** Ships a paid order while preserving Warehouse, Product, Inventory lock order. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void shipForOrder(
      String warehouseId, Map<String, Long> quantities, String orderId, String createdBy) {
    validateCreatedBy(createdBy);
    getWarehouseForUpdate(warehouseId);
    List<String> productIds = quantities.keySet().stream().sorted().toList();
    for (String productId : productIds) getProductForUpdate(productId);
    List<Inventory> inventories = new ArrayList<>();
    for (String productId : productIds) {
      inventories.add(getInventoryForUpdate(productId, warehouseId));
    }
    for (Inventory inventory : inventories) {
      long quantity = quantities.get(inventory.getProduct().getId());
      long onHandBefore = inventory.getOnHand();
      long reservedBefore = inventory.getReserved();
      if (quantity <= 0 || quantity > onHandBefore || quantity > reservedBefore) {
        throw new InsufficientInventoryException(
            "Insufficient reserved or on-hand inventory for shipment");
      }
      long onHandAfter = subtractExact(onHandBefore, quantity);
      long reservedAfter = subtractExact(reservedBefore, quantity);
      inventory.setOnHand(onHandAfter);
      inventory.setReserved(reservedAfter);
      Inventory saved = saveInventory(inventory);
      saveMovement(
          saved,
          InventoryMovementType.SHIPMENT,
          Math.negateExact(quantity),
          Math.negateExact(quantity),
          onHandBefore,
          onHandAfter,
          reservedBefore,
          reservedAfter,
          orderId,
          "Sales order shipment",
          createdBy);
    }
  }

  private Inventory getInventoryForUpdate(String productId, String warehouseId) {
    return inventoryRepository
        .findByProductIdAndWarehouseIdForUpdate(productId, warehouseId)
        .orElseThrow(
            () ->
                new InventoryNotFoundException(
                    "Inventory for product %s and warehouse %s not found"
                        .formatted(productId, warehouseId)));
  }

  public record ReservedProduct(Product product, long quantity) {}

  public record ReservationBatch(Warehouse warehouse, List<ReservedProduct> products) {}

  private long addExact(long current, long delta) {
    try {
      return Math.addExact(current, delta);
    } catch (ArithmeticException exception) {
      throw new InvalidInventoryQuantityException("Inventory quantity exceeds the supported range");
    }
  }

  private long subtractExact(long current, long delta) {
    try {
      return Math.subtractExact(current, delta);
    } catch (ArithmeticException exception) {
      throw new InvalidInventoryQuantityException("Inventory quantity exceeds the supported range");
    }
  }

  private String normalizeRequiredAuditField(String value, String name, int maxCodePoints) {
    String normalized = BusinessText.normalizeOptional(value);
    if (normalized == null) {
      throw new InvalidInventoryQuantityException("Inventory %s must not be blank".formatted(name));
    }
    validateAuditFieldLength(normalized, name, maxCodePoints);
    return normalized;
  }

  private String normalizeOptionalAuditField(String value, String name, int maxCodePoints) {
    String normalized = BusinessText.normalizeOptional(value);
    if (normalized != null) {
      validateAuditFieldLength(normalized, name, maxCodePoints);
    }
    return normalized;
  }

  private void validateAuditFieldLength(String value, String name, int maxCodePoints) {
    if (BusinessText.codePointLength(value) > maxCodePoints) {
      throw new InvalidInventoryQuantityException("Inventory %s is too long".formatted(name));
    }
  }

  private void validateCreatedBy(String createdBy) {
    if (createdBy == null
        || createdBy.isBlank()
        || createdBy.length() > InventoryMovement.CREATED_BY_MAX_LENGTH) {
      throw new IllegalArgumentException("Inventory movement creator is invalid");
    }
  }

  private boolean isProductWarehouseConstraint(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && isNamedConstraint(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  private boolean isNamedConstraint(String constraintName) {
    if (constraintName == null) {
      return false;
    }
    String normalized = constraintName.toLowerCase(java.util.Locale.ROOT);
    String expected =
        Inventory.PRODUCT_WAREHOUSE_UNIQUE_CONSTRAINT.toLowerCase(java.util.Locale.ROOT);
    return normalized.equals(expected)
        || normalized.startsWith(expected + " ")
        || normalized.contains("." + expected + " ");
  }
}
