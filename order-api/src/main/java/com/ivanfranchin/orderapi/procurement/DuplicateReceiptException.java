package com.ivanfranchin.orderapi.procurement;

public class DuplicateReceiptException extends RuntimeException {
  public DuplicateReceiptException(String m) {
    super(m);
  }
}
