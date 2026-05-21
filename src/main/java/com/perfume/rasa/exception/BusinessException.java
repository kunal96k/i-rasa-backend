package com.perfume.rasa.exception;

import org.springframework.http.HttpStatus;

/**
 * Custom exception for business logic errors
 */
public class BusinessException extends RuntimeException {
    private final HttpStatus statusCode;

    public BusinessException(String message) {
        super(message);
        this.statusCode = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String message, HttpStatus statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = HttpStatus.BAD_REQUEST;
    }

    public HttpStatus getStatusCode() {
        return statusCode;
    }
}
