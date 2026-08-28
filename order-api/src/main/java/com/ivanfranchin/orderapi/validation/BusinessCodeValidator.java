package com.ivanfranchin.orderapi.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BusinessCodeValidator implements ConstraintValidator<ValidBusinessCode, String> {

  private int max;

  @Override
  public void initialize(ValidBusinessCode constraint) {
    max = constraint.max();
  }

  @Override
  public boolean isValid(String code, ConstraintValidatorContext context) {
    return BusinessCode.isValid(code, max);
  }
}
