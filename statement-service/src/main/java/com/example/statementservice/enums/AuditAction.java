package com.example.statementservice.enums;

public enum AuditAction {
    DOWNLOAD_SUCCESS("DOWNLOAD_SUCCESS"),
    DOWNLOAD_FAILED("DOWNLOAD_FAILED"),
    UPLOAD_SUCCESS("UPLOAD_SUCCESS");

    private final String value;

    AuditAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
