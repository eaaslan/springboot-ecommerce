package com.backendguru.orderservice.outbox;

public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
