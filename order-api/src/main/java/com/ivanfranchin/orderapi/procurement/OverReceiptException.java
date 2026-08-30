package com.ivanfranchin.orderapi.procurement;

public class OverReceiptException extends RuntimeException {
  public OverReceiptException(String m) {
    super(m);
  }
}
