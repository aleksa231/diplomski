package com.benchmark.nosqlblocking.exception;

/**
 * Raised when a downstream dependency (currently Redis) is unavailable and the operation
 * cannot be served. Mapped to HTTP 503 by the global exception handler so callers fail
 * fast instead of hanging.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
