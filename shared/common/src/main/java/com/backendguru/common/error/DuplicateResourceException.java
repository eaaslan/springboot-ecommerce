package com.backendguru.common.error;

public class DuplicateResourceException extends BusinessException {

  public DuplicateResourceException(String message) {
    super(ErrorCode.DUPLICATE_RESOURCE, message);
  }
}
