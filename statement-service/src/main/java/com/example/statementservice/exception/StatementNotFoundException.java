package com.example.statementservice.exception;

public class StatementNotFoundException extends RuntimeException {
    public StatementNotFoundException(String message) {
        super(message);
    }

    public StatementNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
