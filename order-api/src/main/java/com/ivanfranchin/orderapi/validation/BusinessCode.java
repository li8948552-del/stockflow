package com.ivanfranchin.orderapi.validation;

import java.util.Locale;

public final class BusinessCode {

  private BusinessCode() {}

  public static String normalize(String code) {
    if (code == null) {
      return null;
    }

    int start = 0;
    int end = code.length();
    while (start < end) {
      int codePoint = code.codePointAt(start);
      if (!isBoundaryWhitespace(codePoint)) {
        break;
      }
      start += Character.charCount(codePoint);
    }
    while (start < end) {
      int codePoint = code.codePointBefore(end);
      if (!isBoundaryWhitespace(codePoint)) {
        break;
      }
      end -= Character.charCount(codePoint);
    }

    return code.substring(start, end).toUpperCase(Locale.ROOT);
  }

  public static boolean isValid(String code, int maxCodePoints) {
    String normalizedCode = normalize(code);
    return normalizedCode != null
        && !normalizedCode.isBlank()
        && normalizedCode.codePointCount(0, normalizedCode.length()) <= maxCodePoints;
  }

  public static String normalizeAndValidate(String code, int maxCodePoints) {
    String normalizedCode = normalize(code);
    if (normalizedCode == null || normalizedCode.isBlank()) {
      throw new IllegalArgumentException("Business code must not be blank after normalization");
    }
    if (normalizedCode.codePointCount(0, normalizedCode.length()) > maxCodePoints) {
      throw new IllegalArgumentException(
          "Business code must contain at most %d Unicode code points after normalization"
              .formatted(maxCodePoints));
    }
    return normalizedCode;
  }

  private static boolean isBoundaryWhitespace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }
}
