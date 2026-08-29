package com.ivanfranchin.orderapi.rest.dto;

import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdScalarSerializer;

public final class PlainBigDecimalSerializer extends StdScalarSerializer<BigDecimal> {

  public PlainBigDecimalSerializer() {
    super(BigDecimal.class);
  }

  @Override
  public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context)
      throws JacksonException {
    generator.writeString(value.toPlainString());
  }
}
