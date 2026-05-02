package com.backendguru.orderservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

  private final MeterRegistry registry;
  private final Timer placeOrderTimer;

  public OrderMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.placeOrderTimer =
        Timer.builder("orders.placeOrder.duration")
            .description("End-to-end placeOrder saga duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
  }

  public void incrementPlaced(String currency) {
    Counter.builder("orders.placed")
        .tag("currency", currency == null ? "UNKNOWN" : currency)
        .description("Orders that reached CONFIRMED")
        .register(registry)
        .increment();
  }

  public void incrementCancelled(String reason) {
    Counter.builder("orders.cancelled")
        .tag("reason", reason)
        .description("Orders that ended in CANCELLED")
        .register(registry)
        .increment();
  }

  public Timer placeOrderTimer() {
    return placeOrderTimer;
  }
}
