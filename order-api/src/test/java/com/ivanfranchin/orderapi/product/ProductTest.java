package com.ivanfranchin.orderapi.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTest {

  @Test
  void onPrePersist_generatesIdNormalizesSkuAndSetsTimestamps() {
    Product product = new Product("\u2003sku-1\u00a0", "Keyboard", new BigDecimal("89.95"), 10);

    product.onPrePersist();

    assertThat(product.getId()).isNotBlank();
    assertThat(product.getSku()).isEqualTo("SKU-1");
    assertThat(product.getCreatedAt()).isNotNull();
    assertThat(product.getUpdatedAt()).isNotNull();
    assertThat(product.isActive()).isTrue();
  }

  @Test
  void onPreUpdate_normalizesSkuAndRefreshesUpdatedAt() {
    Product product = new Product("SKU-1", "Keyboard", new BigDecimal("89.95"), 10);
    product.onPrePersist();
    Instant previousUpdatedAt = Instant.EPOCH;
    product.setUpdatedAt(previousUpdatedAt);
    product.setSku("\u00a0new sku\u2003");

    product.onPreUpdate();

    assertThat(product.getSku()).isEqualTo("NEW SKU");
    assertThat(product.getUpdatedAt()).isAfter(previousUpdatedAt);
  }

  @Test
  void constructorAndSetterRejectMoreThan64CodePoints() {
    String invalidSku = "\ud83d\udce6".repeat(Product.SKU_MAX_LENGTH + 1);

    assertThatThrownBy(() -> new Product(invalidSku, "Keyboard", new BigDecimal("89.95"), 10))
        .isInstanceOf(IllegalArgumentException.class);

    Product product = new Product("SKU-1", "Keyboard", new BigDecimal("89.95"), 10);
    assertThatThrownBy(() -> product.setSku(invalidSku))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
