package com.ivanfranchin.orderapi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = BusinessCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBusinessCode {

  String message() default "must be nonblank and within the maximum length after normalization";

  int max() default 64;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
