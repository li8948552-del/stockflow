package com.ivanfranchin.orderapi.order;

public class OrderShipmentConflictException extends RuntimeException {
  public OrderShipmentConflictException(String message) {
    super(message);
  }
}
