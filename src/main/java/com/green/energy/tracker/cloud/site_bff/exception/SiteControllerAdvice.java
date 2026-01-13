package com.green.energy.tracker.cloud.site_bff.exception;

import com.green.energy.tracker.cloud.sitebff.web.model.ApiErrorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class SiteControllerAdvice {
    // ResponseStatusException handler
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiErrorDto>> handleResponseStatusException(ResponseStatusException ex, ServerWebExchange exchange) {
        log.warn("Request failed with status {}: {}", ex.getStatusCode(), ex.getReason());

        var apiError = new ApiErrorDto();
        apiError.setTimestamp(OffsetDateTime.now());
        apiError.setStatus(ex.getStatusCode().value());
        String errorPhrase = (ex.getStatusCode() instanceof HttpStatus status)
                ? status.getReasonPhrase()
                : ex.getStatusCode().toString();
        apiError.setError(errorPhrase);
        apiError.setMessage(ex.getReason());
        apiError.setPath(exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(apiError));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiErrorDto>> handleValidationErrors(WebExchangeBindException ex, ServerWebExchange exchange) {
        log.error("WebExchange error: ", ex);

        Map<String, String> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.nonNull(error.getDefaultMessage()) ? error.getDefaultMessage() : "Unknown error",
                        (existing, replacement) -> existing
                ));

        var apiError = new ApiErrorDto();
        apiError.setTimestamp(OffsetDateTime.now());
        apiError.setStatus(HttpStatus.BAD_REQUEST.value());
        apiError.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
        apiError.setMessage("Validation failed for request");
        apiError.setPath(exchange.getRequest().getPath().value());
        apiError.setValidationErrors(errors);

        return Mono.just(ResponseEntity.badRequest().body(apiError));
    }

    @ExceptionHandler(SiteProcessingException.class)
    public Mono<ResponseEntity<ApiErrorDto>> handleSiteProcessingException(SiteProcessingException ex, ServerWebExchange exchange) {
        log.error("Site processing exception: ", ex);

        var apiError = new ApiErrorDto();
        apiError.setTimestamp(OffsetDateTime.now());
        apiError.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        apiError.setError("Site Processing Error");
        apiError.setMessage(ex.getMessage());
        apiError.setPath(exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiError));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiErrorDto>> handleGenericException(Exception ex, ServerWebExchange exchange) {
        log.error("Unexpected error: ", ex);

        var apiError = new ApiErrorDto();
        apiError.setTimestamp(OffsetDateTime.now());
        apiError.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        apiError.setError(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        apiError.setMessage("An unexpected error occurred. Please contact support.");
        apiError.setPath(exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError));
    }
}