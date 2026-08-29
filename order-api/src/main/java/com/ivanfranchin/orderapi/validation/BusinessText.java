package com.ivanfranchin.orderapi.validation;

public final class BusinessText {

  private BusinessText() {}

  public static String normalizeBoundaryWhitespace(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    int start = 0;
    int end = value.length();
    while (start < end) {
      int codePoint = value.codePointAt(start);
      if (!isBoundaryWhitespace(codePoint)) {
        break;
      }
      start += Character.charCount(codePoint);
    }
    while (end > start) {
      int codePoint = value.codePointBefore(end);
      if (!isBoundaryWhitespace(codePoint)) {
        break;
      }
      end -= Character.charCount(codePoint);
    }
    return value.substring(start, end);
  }

  public static String normalizeOptional(String value) {
    String normalized = normalizeBoundaryWhitespace(value);
    return normalized == null || normalized.isEmpty() ? null : normalized;
  }

  public static boolean isValid(String value, int maxCodePoints, boolean required) {
    String normalized = normalizeOptional(value);
    if (normalized == null) {
      return !required;
    }
    return normalized.codePointCount(0, normalized.length()) <= maxCodePoints;
  }

  public static int codePointLength(String value) {
    return value.codePointCount(0, value.length());
  }

  private static boolean isBoundaryWhitespace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }
}
