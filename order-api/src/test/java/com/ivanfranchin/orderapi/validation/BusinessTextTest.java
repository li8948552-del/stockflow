package com.ivanfranchin.orderapi.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessTextTest {

  @Test
  void removesUnicodeBoundaryWhitespaceButPreservesInternalWhitespaceAndCase() {
    assertThat(BusinessText.normalizeOptional("\u00a0\u2003 AbC\u2003 XyZ \u2003\u00a0"))
        .isEqualTo("AbC\u2003 XyZ");
  }

  @Test
  void unicodeOnlyOptionalTextNormalizesToNull() {
    assertThat(BusinessText.normalizeOptional("\u00a0\u2003 \t")).isNull();
  }

  @Test
  void validatesLengthByUnicodeCodePointAfterNormalization() {
    assertThat(BusinessText.isValid(" \u00a0" + "📦".repeat(128) + "\u2003", 128, true)).isTrue();
    assertThat(BusinessText.isValid("📦".repeat(129), 128, true)).isFalse();
    assertThat(BusinessText.isValid("\u00a0\u2003", 128, true)).isFalse();
  }
}
