package com.aichangeguard.common.error;

import com.aichangeguard.configuration.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler providing a uniform, safe machine-readable error envelope.
 * Strictly avoids leaking stack traces, internal paths, or credentials to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ChangeGuardException.class)
    public ResponseEntity<ErrorResponse> handleChangeGuardException(ChangeGuardException ex) {
        String correlationId = currentCorrelationId();
        ErrorCode code = ex.getErrorCode();

        log.warn("Domain exception [{}] - Code: {}, Message: {}", correlationId, code.getCode(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                code.getCode(),
                ex.getMessage(),
                correlationId,
                ex.getDetails().isEmpty() ? null : ex.getDetails()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String correlationId = currentCorrelationId();
        Map<String, Object> fieldErrors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Payload validation failed [{}] - Fields: {}", correlationId, fieldErrors.keySet());

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR.getCode(),
                "Validation failed for one or more fields",
                correlationId,
                Map.of("fieldErrors", fieldErrors)
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String correlationId = currentCorrelationId();
        log.warn("Constraint violation [{}] - {}", correlationId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ex.getMessage(),
                correlationId
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        String correlationId = currentCorrelationId();
        log.info("Resource not found [{}] - Path: {}", correlationId, ex.getResourcePath());

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "The requested resource was not found: " + ex.getResourcePath(),
                correlationId
        );
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String correlationId = currentCorrelationId();
        log.info("HTTP method not supported [{}] - Method: {}", correlationId, ex.getMethod());

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.METHOD_NOT_ALLOWED.getCode(),
                "HTTP method '%s' is not supported for this endpoint".formatted(ex.getMethod()),
                correlationId
        );
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncaughtException(Exception ex) {
        String correlationId = currentCorrelationId();
        log.error("Unhandled platform exception [{}]", correlationId, ex);

        // Safe message without exposing internal stack trace or host details
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                "An unexpected internal error occurred. Please reference correlationId: " + correlationId,
                correlationId
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        return correlationId != null ? correlationId : "unknown";
    }
}
