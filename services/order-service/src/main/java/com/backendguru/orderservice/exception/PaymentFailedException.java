package com.backendguru.orderservice.exception;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;

public class PaymentFailedException extends BusinessException {
  public PaymentFailedException(String message) {
    super(ErrorCode.PAYMENT_FAILED, message);
  }
}
