package com.ivanfranchin.orderapi.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import(ProductService.class)
class ProductPersistenceTest {

  @Autowired private ProductRepository productRepository;

  @Autowired private ProductService productService;

  @Autowired private EntityManager entityManager;

  @Test
  void createProduct_rejectsWhitespaceAndCaseSkuVariants() {
    productService.createProduct(product("sku-1", "Keyboard", "12.99"));

    assertThatThrownBy(
            () -> productService.createProduct(product("\u2003SKU-1\u00a0", "Another", "12.99")))
        .isInstanceOf(DuplicateSkuException.class);

    assertThat(productRepository.count()).isEqualTo(1);
    assertThat(productRepository.findAll().getFirst().getSku()).isEqualTo("SKU-1");
  }

  @Test
  void entityNormalizationBackstop_preservesUniqueSkuConstraint() {
    productRepository.saveAndFlush(product("sku-1", "Keyboard", "12.99"));

    assertThatThrownBy(
            () -> productRepository.saveAndFlush(product("\u00a0SKU-1\u2003", "Another", "12.99")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void createProduct_preservesValidTwoDecimalPriceExactly() {
    Product saved = productService.createProduct(product("sku-1", "Keyboard", "12.99"));

    entityManager.clear();
    Product reloaded = productRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getPrice()).isEqualByComparingTo("12.99");
    assertThat(reloaded.getPrice().scale()).isEqualTo(2);
  }

  @Test
  void databasePreservesSkuWith64SupplementaryCodePoints() {
    String sku = "\ud83d\udce6".repeat(Product.SKU_MAX_LENGTH);

    Product saved = productService.createProduct(product(sku, "Keyboard", "12.99"));
    entityManager.clear();

    assertThat(productRepository.findById(saved.getId()).orElseThrow().getSku()).isEqualTo(sku);
  }

  @Test
  void directRepositoryCreateRejectsMoreThan64CodePoints() {
    Product product = product("SKU-1", "Keyboard", "12.99");
    ReflectionTestUtils.setField(product, "sku", "\ud83d\udce6".repeat(Product.SKU_MAX_LENGTH + 1));

    assertThatThrownBy(() -> productRepository.saveAndFlush(product))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
            "Business code must contain at most 64 Unicode code points after normalization");
  }

  @Test
  void directRepositoryUpdateRejectsMoreThan64CodePoints() {
    Product product = productRepository.saveAndFlush(product("SKU-1", "Keyboard", "12.99"));
    ReflectionTestUtils.setField(product, "sku", "\ud83d\udce6".repeat(Product.SKU_MAX_LENGTH + 1));

    assertThatThrownBy(entityManager::flush)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("64 Unicode code points");
  }

  @Test
  void createProduct_doesNotReportUnrelatedIntegrityViolationAsDuplicateSku() {
    String oversizedName = "x".repeat(Product.NAME_MAX_LENGTH + 1);

    assertThatThrownBy(() -> productService.createProduct(product("sku-1", oversizedName, "12.99")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateSkuException.class);
  }

  private Product product(String sku, String name, String price) {
    return new Product(sku, name, new BigDecimal(price), 10);
  }
}
