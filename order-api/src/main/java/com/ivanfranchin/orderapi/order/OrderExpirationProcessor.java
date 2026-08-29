package com.ivanfranchin.orderapi.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderExpirationProcessor {
  private final OrderService orderService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(String orderId) {
    orderService.expireOrder(orderId);
  }
}
