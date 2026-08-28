package com.ivanfranchin.orderapi.product;

import com.ivanfranchin.orderapi.validation.BusinessCode;

public final class ProductSku {

  private ProductSku() {}

  public static String normalize(String sku) {
    return BusinessCode.normalize(sku);
  }

  public static boolean isValid(String sku) {
    return BusinessCode.isValid(sku, Product.SKU_MAX_LENGTH);
  }

  public static String normalizeAndValidate(String sku) {
    return BusinessCode.normalizeAndValidate(sku, Product.SKU_MAX_LENGTH);
  }
}
