package com.ivanfranchin.orderapi.product.validation;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductSku;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ProductSkuValidator implements ConstraintValidator<ValidProductSku, String> {

  @Override
  public boolean isValid(String sku, ConstraintValidatorContext context) {
    String normalizedSku = ProductSku.normalize(sku);
    return normalizedSku != null
        && !normalizedSku.isBlank()
        && normalizedSku.length() <= Product.SKU_MAX_LENGTH;
  }
}
