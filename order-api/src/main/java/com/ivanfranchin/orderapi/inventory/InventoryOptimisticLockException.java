package com.ivanfranchin.orderapi.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InventoryOptimisticLockException extends RuntimeException {
  public InventoryOptimisticLockException(String message, Throwable cause) {
    super(message, cause);
  }
}
