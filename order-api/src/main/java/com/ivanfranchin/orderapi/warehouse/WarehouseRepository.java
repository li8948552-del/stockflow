package com.ivanfranchin.orderapi.warehouse;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends JpaRepository<Warehouse, String> {
  List<Warehouse> findAllByOrderByCreatedAtDesc();

  boolean existsByWarehouseCode(String warehouseCode);

  boolean existsByWarehouseCodeAndIdNot(String warehouseCode, String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select warehouse from Warehouse warehouse where warehouse.id = :id")
  Optional<Warehouse> findByIdForUpdate(@Param("id") String id);
}
