package com.turontechnologies.tcoop.auth;

/** Thrown when the OTP email genuinely fails to send — caught by GlobalExceptionHandler. */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
