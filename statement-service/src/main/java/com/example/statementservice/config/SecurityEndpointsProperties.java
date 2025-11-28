package com.example.statementservice.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.endpoints")
public class SecurityEndpointsProperties {

    /** Public endpoints permitted without authentication. */
    private List<String> whitelist = new ArrayList<>();

    /** Admin endpoints that require authentication. */
    private List<String> admin = new ArrayList<>();

    /** Audit endpoints that require authentication. */
    private List<String> audit = new ArrayList<>();

    /** Endpoints for which CSRF is ignored/disabled. */
    private List<String> csrfIgnored = new ArrayList<>();
}
