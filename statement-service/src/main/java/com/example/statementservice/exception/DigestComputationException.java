package com.example.statementservice.exception;

/**
 * Thrown when computing the file's digest fails unexpectedly.
 */
public class DigestComputationException extends RuntimeException {
    public DigestComputationException(String message) {
        super(message);
    }

    public DigestComputationException(String message, Throwable cause) {
        super(message, cause);
    }
}
