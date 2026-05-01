package com.backendguru.cartservice.exception;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;

public class ProductUnavailableException extends BusinessException {

  public ProductUnavailableException(String message) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message);
  }

  public ProductUnavailableException(String message, Throwable cause) {
    super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
  }
}
