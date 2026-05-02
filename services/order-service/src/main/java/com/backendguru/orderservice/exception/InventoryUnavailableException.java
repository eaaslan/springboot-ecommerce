package com.backendguru.orderservice.exception;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;

public class InventoryUnavailableException extends BusinessException {
  public InventoryUnavailableException(String message) {
    super(ErrorCode.INSUFFICIENT_STOCK, message);
  }
}
