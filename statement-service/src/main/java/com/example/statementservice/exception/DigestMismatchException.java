package com.example.statementservice.exception;

/**
 * Thrown when the provided digest does not match the computed file digest.
 */
public class DigestMismatchException extends RuntimeException {
    public DigestMismatchException(String message) {
        super(message);
    }

    public DigestMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
