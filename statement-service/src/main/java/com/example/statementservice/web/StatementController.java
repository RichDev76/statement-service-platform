package com.example.statementservice.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple placeholder controller just so the app has at least one endpoint.
 * Your real statement endpoints would live here.
 */
@RestController
public class StatementController {

    @GetMapping("/api/v1/statements/healthcheck")
    public ResponseEntity<String> healthcheck() {
        return ResponseEntity.ok("OK");
    }
}
