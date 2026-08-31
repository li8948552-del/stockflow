package com.ivanfranchin.orderapi.procurement;

public class InvalidPurchaseOrderStateException extends RuntimeException {
  public InvalidPurchaseOrderStateException(String m) {
    super(m);
  }
}
