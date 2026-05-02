package com.backendguru.orderservice.client.dto;

import java.math.BigDecimal;

public record ChargeRequest(Long orderId, BigDecimal amount, String currency, CardDetails card) {

  public record CardDetails(
      String holderName, String number, String expireMonth, String expireYear, String cvc) {}
}
