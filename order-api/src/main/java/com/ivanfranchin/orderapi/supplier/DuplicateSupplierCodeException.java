package com.ivanfranchin.orderapi.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateSupplierCodeException extends RuntimeException {
  public DuplicateSupplierCodeException(String message) {
    super(message);
  }
}
