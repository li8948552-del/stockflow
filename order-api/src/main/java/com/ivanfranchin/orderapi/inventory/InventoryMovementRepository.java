package com.ivanfranchin.orderapi.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, String> {
  @Query(
      """
      select m from InventoryMovement m
      join fetch m.inventory
      join fetch m.product
      join fetch m.warehouse
      where m.inventory.id = :inventoryId
      order by m.createdAt desc, m.id desc
      """)
  List<InventoryMovement> findDetailedByInventoryIdOrderByCreatedAtDescIdDesc(
      @Param("inventoryId") String inventoryId);
}
