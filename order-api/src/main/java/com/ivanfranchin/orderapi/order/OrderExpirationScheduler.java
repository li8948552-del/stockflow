package com.ivanfranchin.orderapi.order;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OrderExpirationScheduler {
  private static final Logger log = LoggerFactory.getLogger(OrderExpirationScheduler.class);
  private final OrderRepository orderRepository;
  private final OrderExpirationProcessor processor;
  private final Clock clock;

  @Value("${app.order-expiration.enabled:false}")
  private boolean enabled;

  @Value("${app.order-expiration.batch-size:100}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${app.order-expiration.interval-ms:60000}")
  public void expireOrders() {
    if (!enabled) return;
    List<String> orderIds;
    try {
      orderIds =
          orderRepository.findExpiredIds(
              OrderStatus.RESERVED, Instant.now(clock), PageRequest.of(0, Math.max(1, batchSize)));
    } catch (RuntimeException exception) {
      log.warn("Order expiration batch query failed: {}", exception.getClass().getSimpleName());
      return;
    }
    for (String id : orderIds) {
      try {
        processor.process(id);
      } catch (RuntimeException exception) {
        log.warn(
            "Order expiration failed for order {}: {}", id, exception.getClass().getSimpleName());
      }
    }
  }
}
