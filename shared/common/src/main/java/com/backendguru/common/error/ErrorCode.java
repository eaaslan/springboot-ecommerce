package com.backendguru.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested resource was not found"),
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Operation is not permitted for this principal"),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request payload failed validation"),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Downstream service is unavailable"),
  PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "Payment was declined"),
  INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock for one or more items"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

  private final HttpStatus status;
  private final String defaultMessage;
}
