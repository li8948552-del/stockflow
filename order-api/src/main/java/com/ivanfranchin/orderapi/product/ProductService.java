package com.ivanfranchin.orderapi.product;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductService {

  private final ProductRepository productRepository;

  @Transactional(readOnly = true)
  public List<Product> getProducts() {
    return productRepository.findAllByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public Product validateAndGetProduct(String id) {
    return productRepository
        .findById(id)
        .orElseThrow(
            () -> new ProductNotFoundException("Product with id %s not found".formatted(id)));
  }

  @Transactional
  public Product createProduct(Product product) {
    product.setSku(normalizeSku(product.getSku()));
    if (productRepository.existsBySku(product.getSku())) {
      throw duplicateSku(product.getSku());
    }
    return save(product);
  }

  @Transactional
  public Product updateProduct(
      String id, String sku, String name, BigDecimal price, Integer reorderPoint, boolean active) {
    Product product = validateAndGetProduct(id);
    String normalizedSku = normalizeSku(sku);
    if (productRepository.existsBySkuAndIdNot(normalizedSku, id)) {
      throw duplicateSku(normalizedSku);
    }
    product.setSku(normalizedSku);
    product.setName(name);
    product.setPrice(price);
    product.setReorderPoint(reorderPoint);
    product.setActive(active);
    return save(product);
  }

  @Transactional
  public Product deactivateProduct(String id) {
    Product product = validateAndGetProduct(id);
    product.setActive(false);
    return productRepository.save(product);
  }

  private Product save(Product product) {
    try {
      return productRepository.saveAndFlush(product);
    } catch (DataIntegrityViolationException exception) {
      if (isSkuUniqueConstraintViolation(exception)) {
        throw duplicateSku(product.getSku());
      }
      throw exception;
    }
  }

  private String normalizeSku(String sku) {
    return ProductSku.normalize(sku);
  }

  private boolean isSkuUniqueConstraintViolation(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolation
          && Product.SKU_UNIQUE_CONSTRAINT.equalsIgnoreCase(
              constraintViolation.getConstraintName())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private DuplicateSkuException duplicateSku(String sku) {
    return new DuplicateSkuException("Product with SKU %s already exists".formatted(sku));
  }
}
