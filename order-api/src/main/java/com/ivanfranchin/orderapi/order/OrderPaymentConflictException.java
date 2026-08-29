package com.ivanfranchin.orderapi.order;

public class OrderPaymentConflictException extends RuntimeException {
  public OrderPaymentConflictException(String message) {
    super(message);
  }
}
