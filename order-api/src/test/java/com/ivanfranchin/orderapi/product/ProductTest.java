package com.ivanfranchin.orderapi.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTest {

  @Test
  void onPrePersist_generatesIdNormalizesSkuAndSetsTimestamps() {
    Product product = new Product(" sku-1 ", "Keyboard", new BigDecimal("89.95"), 10);

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
    product.setSku("new-sku");

    product.onPreUpdate();

    assertThat(product.getSku()).isEqualTo("NEW-SKU");
    assertThat(product.getUpdatedAt()).isAfter(previousUpdatedAt);
  }
}
