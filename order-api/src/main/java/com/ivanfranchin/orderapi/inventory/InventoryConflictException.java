package com.ivanfranchin.orderapi.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InventoryConflictException extends RuntimeException {
  public InventoryConflictException(String message) {
    super(message);
  }
}
