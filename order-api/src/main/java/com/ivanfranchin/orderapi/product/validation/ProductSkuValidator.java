package com.ivanfranchin.orderapi.product.validation;

import com.ivanfranchin.orderapi.product.ProductSku;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ProductSkuValidator implements ConstraintValidator<ValidProductSku, String> {

  @Override
  public boolean isValid(String sku, ConstraintValidatorContext context) {
    return ProductSku.isValid(sku);
  }
}
