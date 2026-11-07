package com.benchmark.nosqlreactive.controller;

import com.benchmark.common.dto.ErrorResponse;
import com.benchmark.nosqlreactive.exception.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Reactor-native counterpart of the blocking {@code GlobalExceptionHandler}: same status
 * codes, codes and {@link ErrorResponse} shape, but every handler returns a {@link Mono} of
 * the response so nothing blocks the event loop.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotFound(NoSuchElementException ex, ServerWebExchange exchange) {
        return Mono.just(build(HttpStatus.NOT_FOUND, "NOT_FOUND",
                ex.getMessage() != null ? ex.getMessage() : "Resource not found", exchange));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConflict(IllegalStateException ex, ServerWebExchange exchange) {
        return Mono.just(build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), exchange));
    }

    @ExceptionHandler({IllegalArgumentException.class, WebExchangeBindException.class})
    public Mono<ResponseEntity<ErrorResponse>> handleBadRequest(Exception ex, ServerWebExchange exchange) {
        return Mono.just(build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), exchange));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnavailable(ServiceUnavailableException ex,
                                                                 ServerWebExchange exchange) {
        return Mono.just(build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ex.getMessage(), exchange));
    }

    /**
     * WebFlux reports an unmatched route as a {@link ResponseStatusException} carrying 404. Without
     * this handler the generic one below turns it into a 500, which would show up as a server error
     * in the load-test error rate.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return Mono.just(build(status, status.name(), reason, exchange));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneral(Exception ex, ServerWebExchange exchange) {
        return Mono.just(build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", ex.getMessage(), exchange));
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                       String code,
                                                       String message,
                                                       ServerWebExchange exchange) {
        return ResponseEntity.status(status).body(
                ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(status.value())
                        .code(code)
                        .message(message)
                        .path(exchange.getRequest().getURI().getPath())
                        .build()
        );
    }
}
