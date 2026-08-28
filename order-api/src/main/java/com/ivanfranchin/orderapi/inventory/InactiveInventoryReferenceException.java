package com.ivanfranchin.orderapi.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InactiveInventoryReferenceException extends RuntimeException {
  public InactiveInventoryReferenceException(String message) {
    super(message);
  }
}
