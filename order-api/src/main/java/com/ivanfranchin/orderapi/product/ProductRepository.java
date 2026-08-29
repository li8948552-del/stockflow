package com.ivanfranchin.orderapi.product;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

  List<Product> findAllByOrderByCreatedAtDesc();

  boolean existsBySku(String sku);

  boolean existsBySkuAndIdNot(String sku, String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select product from Product product where product.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") String id);
}
