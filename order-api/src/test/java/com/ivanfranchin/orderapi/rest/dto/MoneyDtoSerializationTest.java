package com.ivanfranchin.orderapi.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ivanfranchin.orderapi.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MoneyDtoSerializationTest {
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void productPricesSerializeAsExactDecimalStrings() {
    assertProductPrice("0.10");
    assertProductPrice("12.30");
    assertProductPrice("99999999999999999.99");
    assertProductPrice("1E+2", "100");
  }

  @Test
  void orderAmountsSerializeAsExactDecimalStrings() {
    String maximum = "99999999999999999.99";
    OrderDto dto =
        new OrderDto(
            "order-id",
            new OrderDto.UserSummary(1L, "alice"),
            new OrderDto.WarehouseSummary("warehouse-id", "SYD", "Sydney"),
            OrderStatus.RESERVED,
            List.of(
                new OrderDto.Item(
                    "item-id",
                    "product-id",
                    "SKU-1",
                    "Product",
                    1,
                    1,
                    new BigDecimal("0.10"),
                    new BigDecimal("12.30"))),
            new BigDecimal(maximum),
            Instant.parse("2026-01-01T00:30:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"),
            0L);

    String json = jsonMapper.writeValueAsString(dto);

    assertThat(json).contains("\"unitPrice\":\"0.10\"");
    assertThat(json).contains("\"lineTotal\":\"12.30\"");
    assertThat(json).contains("\"totalAmount\":\"" + maximum + "\"");
    assertThat(json).doesNotContain("E+");
  }

  private void assertProductPrice(String price) {
    assertProductPrice(price, price);
  }

  private void assertProductPrice(String price, String expected) {
    ProductDto dto =
        new ProductDto(
            "product-id",
            "SKU-1",
            "Product",
            new BigDecimal(price),
            0,
            true,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"));

    String json = jsonMapper.writeValueAsString(dto);

    assertThat(json).contains("\"price\":\"" + expected + "\"");
    assertThat(json).doesNotContain("E+");
  }
}
