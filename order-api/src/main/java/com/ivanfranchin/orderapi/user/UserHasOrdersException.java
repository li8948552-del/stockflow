package com.ivanfranchin.orderapi.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserHasOrdersException extends RuntimeException {
  public UserHasOrdersException(String message) {
    super(message);
  }
}
