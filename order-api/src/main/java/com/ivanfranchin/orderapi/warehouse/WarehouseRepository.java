package com.ivanfranchin.orderapi.warehouse;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, String> {
  List<Warehouse> findAllByOrderByCreatedAtDesc();

  boolean existsByWarehouseCode(String warehouseCode);

  boolean existsByWarehouseCodeAndIdNot(String warehouseCode, String id);
}
