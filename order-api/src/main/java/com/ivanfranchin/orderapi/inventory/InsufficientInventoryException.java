package com.ivanfranchin.orderapi.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientInventoryException extends RuntimeException {
  public InsufficientInventoryException(String message) {
    super(message);
  }
}
