package com.benchmark.relational.exception;

/** Raised when a downstream dependency (cart store, external service) is unavailable. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
