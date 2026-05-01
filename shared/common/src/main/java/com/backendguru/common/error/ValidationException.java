package com.backendguru.common.error;

public class ValidationException extends BusinessException {

  public ValidationException(String message) {
    super(ErrorCode.VALIDATION_FAILED, message);
  }
}
