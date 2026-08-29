package com.ivanfranchin.orderapi.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** PostgreSQL TIMESTAMP WITH TIME ZONE stores instants with microsecond precision. */
public final class TimePrecision {
  private TimePrecision() {}

  public static Instant databasePrecision(Instant instant) {
    return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
  }
}
