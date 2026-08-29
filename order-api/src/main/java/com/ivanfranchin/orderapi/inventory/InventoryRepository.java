package com.ivanfranchin.orderapi.inventory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

  Optional<Inventory> findByProductIdAndWarehouseId(String productId, String warehouseId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from Inventory i where i.product.id = :productId and i.warehouse.id = :warehouseId")
  Optional<Inventory> findByProductIdAndWarehouseIdForUpdate(
      @Param("productId") String productId, @Param("warehouseId") String warehouseId);

  @Query(
      """
      select i from Inventory i
      join fetch i.product
      join fetch i.warehouse
      where i.id = :id
      """)
  Optional<Inventory> findDetailedById(@Param("id") String id);

  @Query(
      """
      select i from Inventory i
      join fetch i.product p
      join fetch i.warehouse w
      where (:productId is null or p.id = :productId)
        and (:warehouseId is null or w.id = :warehouseId)
        and (:lowStock = false or (i.onHand - i.reserved) <= p.reorderPoint)
      order by i.updatedAt desc
      """)
  List<Inventory> findInventory(
      @Param("productId") String productId,
      @Param("warehouseId") String warehouseId,
      @Param("lowStock") boolean lowStock);
}
