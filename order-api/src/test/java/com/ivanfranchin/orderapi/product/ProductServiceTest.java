package com.ivanfranchin.orderapi.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ProductService.class)
class ProductServiceTest {

  @MockitoBean private ProductRepository productRepository;

  @Autowired private ProductService productService;

  @Test
  void getProducts_returnsProductsDescending() {
    Product product = product("SKU-1");
    when(productRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(product));

    assertThat(productService.getProducts()).containsExactly(product);

    verify(productRepository).findAllByOrderByCreatedAtDesc();
    verifyNoMoreInteractions(productRepository);
  }

  @Test
  void validateAndGetProduct_returnsProductWhenFound() {
    Product product = product("SKU-1");
    when(productRepository.findById("product-id")).thenReturn(Optional.of(product));

    assertThat(productService.validateAndGetProduct("product-id")).isSameAs(product);
  }

  @Test
  void validateAndGetProduct_throwsWhenMissing() {
    when(productRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.validateAndGetProduct("missing"))
        .isInstanceOf(ProductNotFoundException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void createProduct_normalizesSkuAndSaves() {
    Product product = product("\u2003sku-1\u00a0");
    when(productRepository.existsBySku("SKU-1")).thenReturn(false);
    when(productRepository.saveAndFlush(product)).thenReturn(product);

    assertThat(productService.createProduct(product)).isSameAs(product);
    assertThat(product.getSku()).isEqualTo("SKU-1");
    verify(productRepository).existsBySku("SKU-1");
    verify(productRepository).saveAndFlush(product);
  }

  @Test
  void createProduct_rejectsDuplicateSkuCaseInsensitively() {
    Product product = product("\u00a0sku-1\u2003");
    when(productRepository.existsBySku("SKU-1")).thenReturn(true);

    assertThatThrownBy(() -> productService.createProduct(product))
        .isInstanceOf(DuplicateSkuException.class)
        .hasMessageContaining("SKU-1");
    verify(productRepository).existsBySku("SKU-1");
    verifyNoMoreInteractions(productRepository);
  }

  @Test
  void createAndUpdateRejectMoreThan64CodePointsBeforePersistence() {
    String invalidSku = "\ud83d\udce6".repeat(Product.SKU_MAX_LENGTH + 1);
    Product invalidProduct = mock(Product.class);
    when(invalidProduct.getSku()).thenReturn(invalidSku);

    assertThatThrownBy(() -> productService.createProduct(invalidProduct))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoMoreInteractions(productRepository);

    Product existing = product("SKU-1");
    when(productRepository.findById("product-id")).thenReturn(Optional.of(existing));
    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    "product-id", invalidSku, "Updated", BigDecimal.ONE, 1, true))
        .isInstanceOf(IllegalArgumentException.class);
    verify(productRepository).findById("product-id");
    verifyNoMoreInteractions(productRepository);
  }

  @Test
  void createProduct_convertsDatabaseUniqueConstraintFailure() {
    Product product = product("SKU-1");
    ConstraintViolationException constraintViolation =
        new ConstraintViolationException(
            "duplicate", new SQLException("duplicate"), Product.SKU_UNIQUE_CONSTRAINT);
    when(productRepository.existsBySku("SKU-1")).thenReturn(false);
    when(productRepository.saveAndFlush(product))
        .thenThrow(new DataIntegrityViolationException("duplicate", constraintViolation));

    assertThatThrownBy(() -> productService.createProduct(product))
        .isInstanceOf(DuplicateSkuException.class)
        .hasMessageContaining("SKU-1");
  }

  @Test
  void createProduct_preservesUnrelatedIntegrityFailure() {
    Product product = product("SKU-1");
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException(
            "other constraint",
            new ConstraintViolationException(
                "other", new SQLException("other"), "ck_products_other"));
    when(productRepository.existsBySku("SKU-1")).thenReturn(false);
    when(productRepository.saveAndFlush(product)).thenThrow(failure);

    assertThatThrownBy(() -> productService.createProduct(product)).isSameAs(failure);
  }

  @Test
  void updateProduct_updatesAllFieldsAndNormalizesSku() {
    Product product = product("OLD-SKU");
    when(productRepository.findById("product-id")).thenReturn(Optional.of(product));
    when(productRepository.existsBySkuAndIdNot("NEW-SKU", "product-id")).thenReturn(false);
    when(productRepository.saveAndFlush(product)).thenReturn(product);

    Product result =
        productService.updateProduct(
            "product-id", "\u00a0new sku\u2003", "Updated", new BigDecimal("12.50"), 7, false);

    assertThat(result.getSku()).isEqualTo("NEW SKU");
    assertThat(result.getName()).isEqualTo("Updated");
    assertThat(result.getPrice()).isEqualByComparingTo("12.50");
    assertThat(result.getReorderPoint()).isEqualTo(7);
    assertThat(result.isActive()).isFalse();
  }

  @Test
  void updateProduct_rejectsSkuUsedByAnotherProduct() {
    Product product = product("OLD-SKU");
    when(productRepository.findById("product-id")).thenReturn(Optional.of(product));
    when(productRepository.existsBySkuAndIdNot("TAKEN", "product-id")).thenReturn(true);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    "product-id", "taken", "Updated", BigDecimal.ONE, 1, true))
        .isInstanceOf(DuplicateSkuException.class)
        .hasMessageContaining("TAKEN");
  }

  @Test
  void deactivateProduct_setsActiveFalseWithoutDeleting() {
    Product product = product("SKU-1");
    product.setActive(true);
    when(productRepository.findById("product-id")).thenReturn(Optional.of(product));
    when(productRepository.save(product)).thenReturn(product);

    Product result = productService.deactivateProduct("product-id");

    assertThat(result.isActive()).isFalse();
    verify(productRepository).save(product);
  }

  private Product product(String sku) {
    return new Product(sku, "Keyboard", new BigDecimal("89.95"), 10);
  }
}
