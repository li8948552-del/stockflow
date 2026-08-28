package com.ivanfranchin.orderapi.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BusinessCodeTest {

  @Test
  void normalize_removesAsciiAndUnicodeBoundaryWhitespace() {
    assertThat(BusinessCode.normalize(" \t\u2003\u00a0code-1\u00a0\u2003\n ")).isEqualTo("CODE-1");
  }

  @Test
  void normalize_preservesInternalWhitespace() {
    assertThat(BusinessCode.normalize("\u2003part\u2003\u00a0 two\u00a0"))
        .isEqualTo("PART\u2003\u00a0 TWO");
  }

  @Test
  void normalize_handlesNullAndUnicodeOnlyWhitespace() {
    assertThat(BusinessCode.normalize(null)).isNull();
    assertThat(BusinessCode.normalize("\u2003\u00a0 \t")).isEmpty();
  }

  @Test
  void normalize_uppercasesWithUnicodeExpansion() {
    assertThat(BusinessCode.normalize("\u2003stra\u00dfe\u00a0")).isEqualTo("STRASSE");
  }

  @Test
  void semanticValidationUsesNormalizedUnicodeCodePoints() {
    String valid = "\u2003" + "\ud83d\udce6".repeat(64) + "\u00a0";
    String invalid = "\ud83d\udce6".repeat(65);

    assertThat(BusinessCode.isValid(valid, 64)).isTrue();
    assertThat(BusinessCode.normalizeAndValidate(valid, 64)).isEqualTo("\ud83d\udce6".repeat(64));
    assertThat(BusinessCode.isValid(invalid, 64)).isFalse();
    assertThatThrownBy(() -> BusinessCode.normalizeAndValidate(invalid, 64))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("64 Unicode code points");
  }
}
