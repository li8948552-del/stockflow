package com.ivanfranchin.orderapi.inventory;

public enum InventoryMovementType {
  INITIAL_STOCK,
  RECEIPT,
  ADJUSTMENT_IN,
  ADJUSTMENT_OUT,
  RESERVATION,
  RELEASE,
  SHIPMENT
}
