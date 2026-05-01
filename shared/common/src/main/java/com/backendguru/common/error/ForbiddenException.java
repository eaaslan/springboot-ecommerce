package com.backendguru.common.error;

public class ForbiddenException extends BusinessException {

  public ForbiddenException(String message) {
    super(ErrorCode.FORBIDDEN, message);
  }
}
