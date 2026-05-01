package com.backendguru.common.error;

public class UnauthorizedException extends BusinessException {

  public UnauthorizedException(String message) {
    super(ErrorCode.UNAUTHORIZED, message);
  }
}
