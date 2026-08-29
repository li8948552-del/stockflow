package com.ivanfranchin.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class OrderExpirationConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(Configuration.class);

  @Test
  void disabledConfigurationKeepsSchedulerSafeAndSkipsExecution() {
    contextRunner
        .withPropertyValues(
            "app.order-expiration.enabled=false",
            "app.order-expiration.interval-ms=1234",
            "app.order-expiration.batch-size=7")
        .run(
            context -> {
              OrderExpirationScheduler scheduler = context.getBean(OrderExpirationScheduler.class);
              assertThat(scheduler).isNotNull();
              scheduler.expireOrders();
              verify(context.getBean(OrderRepository.class), never())
                  .findExpiredIds(
                      org.mockito.ArgumentMatchers.any(),
                      org.mockito.ArgumentMatchers.any(),
                      org.mockito.ArgumentMatchers.any());
            });
  }

  @Test
  void enabledConfigurationBindsPropertiesAndRunsScheduler() {
    contextRunner
        .withPropertyValues(
            "app.order-expiration.enabled=true",
            "app.order-expiration.interval-ms=4321",
            "app.order-expiration.batch-size=3")
        .run(
            context -> {
              OrderExpirationScheduler scheduler = context.getBean(OrderExpirationScheduler.class);
              scheduler.expireOrders();
              verify(context.getBean(OrderRepository.class))
                  .findExpiredIds(
                      org.mockito.ArgumentMatchers.eq(OrderStatus.RESERVED),
                      org.mockito.ArgumentMatchers.any(),
                      org.mockito.ArgumentMatchers.eq(
                          org.springframework.data.domain.PageRequest.of(0, 3)));
            });
  }

  @TestConfiguration
  static class Configuration {
    @Bean
    OrderRepository orderRepository() {
      return mock(OrderRepository.class);
    }

    @Bean
    OrderExpirationProcessor processor() {
      return mock(OrderExpirationProcessor.class);
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    OrderExpirationScheduler scheduler(
        OrderRepository repository, OrderExpirationProcessor processor, Clock clock) {
      return new OrderExpirationScheduler(repository, processor, clock);
    }
  }
}
