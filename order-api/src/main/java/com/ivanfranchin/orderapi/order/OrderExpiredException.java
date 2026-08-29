package com.ivanfranchin.orderapi.order;

public class OrderExpiredException extends RuntimeException {
  public OrderExpiredException(String message) {
    super(message);
  }
}
