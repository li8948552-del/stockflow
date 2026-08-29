package com.ivanfranchin.orderapi.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidOrderStatusException extends RuntimeException {
  public InvalidOrderStatusException(String message) {
    super(message);
  }
}
