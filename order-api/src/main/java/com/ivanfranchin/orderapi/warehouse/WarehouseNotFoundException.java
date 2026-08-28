package com.ivanfranchin.orderapi.warehouse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WarehouseNotFoundException extends RuntimeException {
  public WarehouseNotFoundException(String message) {
    super(message);
  }
}
