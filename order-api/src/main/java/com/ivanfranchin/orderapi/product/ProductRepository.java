package com.ivanfranchin.orderapi.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {

  List<Product> findAllByOrderByCreatedAtDesc();

  boolean existsBySku(String sku);

  boolean existsBySkuAndIdNot(String sku, String id);
}
