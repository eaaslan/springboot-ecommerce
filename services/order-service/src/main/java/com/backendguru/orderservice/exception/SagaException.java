package com.backendguru.orderservice.exception;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;

/** Generic saga-orchestration failure (downstream unreachable, etc.). */
public class SagaException extends BusinessException {
  public SagaException(String message) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message);
  }

  public SagaException(String message, Throwable cause) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
  }
}
