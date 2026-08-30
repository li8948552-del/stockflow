package com.ivanfranchin.orderapi.procurement;

public class PurchaseOrderNotFoundException extends RuntimeException {
  public PurchaseOrderNotFoundException(String m) {
    super(m);
  }
}
