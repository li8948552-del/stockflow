package com.ivanfranchin.orderapi.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {
  private static final Instant NOW = Instant.parse("2026-02-01T00:00:00Z");

  @Mock private OrderRepository orderRepository;
  @Mock private OrderExpirationProcessor processor;
  private OrderExpirationScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new OrderExpirationScheduler(orderRepository, processor, Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(scheduler, "enabled", true);
    ReflectionTestUtils.setField(scheduler, "batchSize", 2);
  }

  @Test
  void emptyCandidatesDoNotInvokeProcessor() {
    when(orderRepository.findExpiredIds(any(), any(), any())).thenReturn(List.of());

    scheduler.expireOrders();

    verify(processor, never()).process(any());
  }

  @Test
  void dueCandidateIdsAreProcessedWithStableBatchQuery() {
    when(orderRepository.findExpiredIds(OrderStatus.RESERVED, NOW, PageRequest.of(0, 2)))
        .thenReturn(List.of("order-1", "order-2"));

    scheduler.expireOrders();

    verify(orderRepository).findExpiredIds(OrderStatus.RESERVED, NOW, PageRequest.of(0, 2));
    verify(processor).process("order-1");
    verify(processor).process("order-2");
  }

  @Test
  void processingContinuesAfterOneOrderFails() {
    when(orderRepository.findExpiredIds(any(), any(), any()))
        .thenReturn(List.of("order-1", "order-2"));
    org.mockito.Mockito.doThrow(new RuntimeException("failed")).when(processor).process("order-1");

    scheduler.expireOrders();

    verify(processor).process("order-1");
    verify(processor).process("order-2");
  }

  @Test
  void disabledSchedulerDoesNotQueryCandidates() {
    ReflectionTestUtils.setField(scheduler, "enabled", false);

    scheduler.expireOrders();

    verify(orderRepository, never()).findExpiredIds(any(), any(), any());
    verify(processor, never()).process(any());
  }

  @Test
  void batchSizeIsClampedToPositiveValue() {
    ReflectionTestUtils.setField(scheduler, "batchSize", 0);
    when(orderRepository.findExpiredIds(OrderStatus.RESERVED, NOW, PageRequest.of(0, 1)))
        .thenReturn(List.of());

    scheduler.expireOrders();

    verify(orderRepository).findExpiredIds(OrderStatus.RESERVED, NOW, PageRequest.of(0, 1));
  }
}
