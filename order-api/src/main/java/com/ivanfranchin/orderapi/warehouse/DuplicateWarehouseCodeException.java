package com.ivanfranchin.orderapi.warehouse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateWarehouseCodeException extends RuntimeException {
  public DuplicateWarehouseCodeException(String message) {
    super(message);
  }
}
