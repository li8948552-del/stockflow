package com.ivanfranchin.orderapi.supplier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, String> {
  List<Supplier> findAllByOrderByCreatedAtDesc();

  boolean existsBySupplierCode(String supplierCode);

  boolean existsBySupplierCodeAndIdNot(String supplierCode, String id);
}
