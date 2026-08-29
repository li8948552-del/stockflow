package com.ivanfranchin.orderapi.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyOrderException extends RuntimeException {
  public EmptyOrderException(String message) {
    super(message);
  }
}
