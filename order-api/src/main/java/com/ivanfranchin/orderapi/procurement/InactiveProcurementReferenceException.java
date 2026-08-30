package com.ivanfranchin.orderapi.procurement;

public class InactiveProcurementReferenceException extends RuntimeException {
  public InactiveProcurementReferenceException(String m) {
    super(m);
  }
}
