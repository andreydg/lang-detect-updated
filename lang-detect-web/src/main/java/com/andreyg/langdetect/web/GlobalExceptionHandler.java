package com.andreyg.langdetect.web;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps uncaught exceptions to the same {@link DetectResponse} JSON shape the
 * client already understands, instead of Spring's default HTML error page.
 */
@RestControllerAdvice(assignableTypes = DetectController.class)
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Malformed or unparseable request body. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<DetectResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(new DetectResponse(null, null, null, null, "Malformed JSON request"));
  }

  /**
   * Anything else. Exceptions that carry their own HTTP status (Spring MVC's
   * {@link ErrorResponse}s, e.g. 415 for a non-JSON body) keep their status and
   * headers. Client errors also keep their message; server errors are logged
   * and get only the standard reason phrase, since their detail can describe
   * internals. Everything else is a server fault: log the detail server-side,
   * return a generic message.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<DetectResponse> handleGeneric(Exception ex) {
    if (ex instanceof ErrorResponse response) {
      HttpStatusCode status = response.getStatusCode();
      String message;
      if (status.is5xxServerError()) {
        log.error("Server error serving /api request", ex);
        HttpStatus standard = HttpStatus.resolve(status.value());
        message = standard != null ? standard.getReasonPhrase() : "Server error";
      } else {
        log.debug("Rejected /api request: {}", ex.getMessage());
        message = Objects.requireNonNullElse(response.getBody().getDetail(), "Request rejected");
      }
      return ResponseEntity.status(status)
          .headers(response.getHeaders())
          .body(new DetectResponse(null, null, null, null, message));
    }
    log.error("Unhandled exception serving /api request", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new DetectResponse(null, null, null, null, "Internal server error"));
  }
}
