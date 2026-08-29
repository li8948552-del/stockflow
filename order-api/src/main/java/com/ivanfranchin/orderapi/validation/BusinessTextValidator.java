package com.ivanfranchin.orderapi.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BusinessTextValidator implements ConstraintValidator<ValidBusinessText, String> {

  private int max;
  private boolean required;

  @Override
  public void initialize(ValidBusinessText annotation) {
    max = annotation.max();
    required = annotation.required();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return BusinessText.isValid(value, max, required);
  }
}
