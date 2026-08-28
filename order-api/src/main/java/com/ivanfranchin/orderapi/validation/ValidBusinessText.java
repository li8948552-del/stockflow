package com.ivanfranchin.orderapi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = BusinessTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBusinessText {

  String message() default "must be valid after Unicode boundary whitespace normalization";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  int max();

  boolean required() default false;
}
