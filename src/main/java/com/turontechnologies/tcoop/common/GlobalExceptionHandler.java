package com.turontechnologies.tcoop.common;

import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every endpoint in this API is documented (api-conventions.md) to return errors as
 * {@code {"error": "..."}}, whatever the status code. Without this, Spring's defaults leak
 * through instead — a validation failure becomes a multi-field JSON blob, a malformed request
 * body becomes an HTML whitelabel page — neither of which the frontend can show a user directly.
 * This is the one place that guarantee is enforced, for every controller, automatically.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .filter(msg -> msg != null && !msg.isBlank())
            .collect(Collectors.joining("; "));
    if (message.isBlank()) {
      message = "Invalid request";
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> handleMalformedBody(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "Request body is missing or malformed"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
      DataIntegrityViolationException ex) {
    // Defense-in-depth: controllers should check uniqueness up front (see ProfileController's
    // email check) so this path is rare in practice — a race between two concurrent requests,
    // or a future unique constraint nobody added an explicit check for yet.
    log.warn("Data integrity violation: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "That value conflicts with an existing record"));
  }

  @ExceptionHandler(EmailDeliveryException.class)
  public ResponseEntity<Map<String, String>> handleEmailDeliveryFailure(EmailDeliveryException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "Something went wrong. Please try again."));
  }
}
