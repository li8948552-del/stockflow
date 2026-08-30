package com.ivanfranchin.orderapi.procurement;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
  @EntityGraph(attributePaths = {"supplier", "warehouse", "items", "items.product"})
  @Query(
      "select distinct p from PurchaseOrder p where (:supplierId is null or p.supplier.id=:supplierId) and (:warehouseId is null or p.warehouse.id=:warehouseId) and (:status is null or p.status=:status) order by p.createdAt desc,p.id desc")
  List<PurchaseOrder> search(
      @Param("supplierId") String supplierId,
      @Param("warehouseId") String warehouseId,
      @Param("status") PurchaseOrderStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PurchaseOrder p where p.id=:id")
  Optional<PurchaseOrder> findByIdForUpdate(@Param("id") String id);

  @EntityGraph(attributePaths = {"supplier", "warehouse", "items", "items.product"})
  @Query("select p from PurchaseOrder p where p.id=:id")
  Optional<PurchaseOrder> findDetailedById(@Param("id") String id);
}
