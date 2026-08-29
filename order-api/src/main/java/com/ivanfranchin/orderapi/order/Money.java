package com.ivanfranchin.orderapi.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {
  private static final int PRECISION = 19;
  private static final int SCALE = 2;

  private Money() {}

  static BigDecimal requireDatabaseValue(BigDecimal value, String field) {
    if (value == null || value.signum() < 0 || value.scale() > SCALE) {
      throw new InvalidOrderException(
          "Order %s must be nonnegative with at most 2 decimals".formatted(field));
    }
    BigDecimal normalized = value.setScale(SCALE, RoundingMode.UNNECESSARY);
    if (normalized.precision() > PRECISION) {
      throw new InvalidOrderException(
          "Order %s exceeds the supported monetary range".formatted(field));
    }
    return normalized;
  }

  static BigDecimal multiply(BigDecimal price, long quantity, String field) {
    if (quantity <= 0)
      throw new InvalidOrderException("Order item quantity must be greater than zero");
    return requireDatabaseValue(price.multiply(BigDecimal.valueOf(quantity)), field);
  }

  static BigDecimal add(BigDecimal left, BigDecimal right) {
    return requireDatabaseValue(left.add(right), "totalAmount");
  }
}
