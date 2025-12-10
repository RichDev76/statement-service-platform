package com.example.statementservice;

public class DecryptionFailedException extends RuntimeException {
    public DecryptionFailedException(String message) {
        super(message);
    }
}
