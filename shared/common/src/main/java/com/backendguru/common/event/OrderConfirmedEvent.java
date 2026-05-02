package com.backendguru.common.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderConfirmedEvent(
    String eventId,
    Long orderId,
    Long userId,
    BigDecimal totalAmount,
    String currency,
    Instant occurredAt) {}
