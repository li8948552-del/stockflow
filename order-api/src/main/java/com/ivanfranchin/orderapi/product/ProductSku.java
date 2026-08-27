package com.ivanfranchin.orderapi.product;

import java.util.Locale;

public final class ProductSku {

  private ProductSku() {}

  public static String normalize(String sku) {
    return sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
  }
}
