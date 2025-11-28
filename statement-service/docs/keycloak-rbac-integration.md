# Keycloak RBAC integration guide (no code applied yet)

This document lists the exact, minimal changes required to migrate the Statement Service from HTTP Basic auth to Keycloak-backed JWT authentication with role-based access control (RBAC). It is designed for copy/paste. No changes have been applied to the codebase — follow these steps to implement.

Contents
- 1) Add dependencies (Maven)
- 2) Configure application.yml for JWT resource server
- 3) Update WebSecurityConfig to use JWT and map Keycloak roles
- 4) Optional: Method-level RBAC
- 5) Update OpenAPI to Bearer JWT
- 6) Keycloak realm/client/role setup
- 7) Verification checklist and curl examples
- 8) Rollback and notes

---

## 1) Add dependencies (Maven)
Add the OAuth2 Resource Server dependency to enable JWT validation. The jose module is pulled transitively, but including it explicitly is fine.

Paste into pom.xml <dependencies>:

```xml
<!-- Keycloak via Spring Security OAuth2 Resource Server (JWT) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  <!-- version managed by Spring Boot BOM -->
  <!-- no explicit <version> needed -->
  <!-- Provides JWT auth and integrates with Spring Security -->
  </dependency>
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-oauth2-jose</artifactId>
  <!-- For Nimbus JWT parsing/validation; usually transitively included -->
</dependency>
```

You can keep spring-boot-starter-security. No Keycloak adapter is needed (deprecated). Spring validates tokens issued by Keycloak via OIDC discovery/JWKs.

---

## 2) Configure application.yml for JWT resource server
Replace HTTP Basic with JWT by configuring the issuer URI (recommended) or the JWK set URI of your Keycloak realm.

Add the following under spring: (edit values for your environment)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Option A: Preferred — OIDC discovery (issuer URI)
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8080/realms/statement-realm}
          # Option B: Direct JWKs (use if issuer discovery is blocked)
          # jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8080/realms/statement-realm/protocol/openid-connect/certs}
          # Use the username-like claim for Principal (optional)
          principal-claim-name: preferred_username

# Optional: declare expected audience for additional validation (see code snippet below)
security:
  auth:
    audience: ${KEYCLOAK_AUDIENCE:statement-service}
```

Notes:
- KEYCLOAK_ISSUER_URI typically looks like https://keycloak.example.com/realms/<realm>.
- If you host Keycloak at a different port/path, update the URIs accordingly.

Your existing security.endpoints.whitelist/admin/audit/csrfIgnored stays as-is and will be enforced with roles below.

---

## 3) Update WebSecurityConfig to use JWT and map Keycloak roles
Replace HTTP Basic with OAuth2 Resource Server (JWT). Map Keycloak roles from both realm_access.roles and resource_access.*.roles to Spring authorities with ROLE_ prefix.

Changes to make in src/main/java/com/example/statementservice/config/WebSecurityConfig.java:

1) Add imports:
```java
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // if using method-level RBAC
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
```

2) Optional but recommended: enable method security at class level:
```java
@EnableMethodSecurity
```

3) Replace httpBasic() with oauth2ResourceServer(jwt):
```java
http
  .authorizeHttpRequests(authz -> authz
      // Public endpoints
      .requestMatchers(endpoints.getWhitelist().toArray(String[]::new)).permitAll()

      // Admin endpoints — require ADMIN role
      .requestMatchers(endpoints.getAdmin().toArray(String[]::new)).hasAnyRole("ADMIN")

      // Audit endpoints — require AUDIT or ADMIN
      .requestMatchers(endpoints.getAudit().toArray(String[]::new)).hasAnyRole("AUDIT", "ADMIN")

      // Everything else must be authenticated
      .anyRequest().authenticated())
  .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  .csrf(csrf -> csrf.ignoringRequestMatchers(endpoints.getCsrfIgnored().toArray(String[]::new)))
  .exceptionHandling(ex -> ex
      .authenticationEntryPoint(problemDetailAuthEntryPoint)
      .accessDeniedHandler(problemDetailAccessDeniedHandler))
  // REPLACE this:
  // .httpBasic(httpBasic -> {})
  // WITH this:
  .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
```

4) Add a JwtAuthenticationConverter bean to extract roles from Keycloak tokens:
```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    scopes.setAuthorityPrefix("SCOPE_");

    return new JwtAuthenticationConverter() {
        @Override
        protected Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
            Set<String> roles = new HashSet<>();

            // Realm roles
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object realmRoles = realmAccess.get("roles");
                if (realmRoles instanceof Collection<?> rr) {
                    rr.stream().map(Object::toString).forEach(roles::add);
                }
            }

            // Client roles across resource_access
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                resourceAccess.values().forEach(client -> {
                    if (client instanceof Map<?, ?> m) {
                        Object clientRoles = m.get("roles");
                        if (clientRoles instanceof Collection<?> cr) {
                            cr.stream().map(Object::toString).forEach(roles::add);
                        }
                    }
                });
            }

            // Map to ROLE_* authorities
            List<GrantedAuthority> roleAuthorities = roles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

            // Include scope-based authorities too (optional)
            Collection<GrantedAuthority> scopeAuthorities = scopes.convert(jwt);

            List<GrantedAuthority> combined = new ArrayList<>(roleAuthorities);
            if (scopeAuthorities != null) combined.addAll(scopeAuthorities);
            return combined;
        }
    };
}
```

5) Optional audience validation (recommended):
Add this bean and property security.auth.audience (see YAML above) to ensure the JWT has the intended audience.
```java
@Bean
public OAuth2TokenValidator<Jwt> audienceValidator(@Value("${security.auth.audience:statement-service}") String audience) {
    return jwt -> {
        List<String> aud = jwt.getAudience();
        if (aud != null && aud.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error err = new OAuth2Error("invalid_token", "The required audience is missing", null);
        return OAuth2TokenValidatorResult.failure(err);
    };
}

@Bean
public JwtDecoder jwtDecoder(NimbusJwtDecoder jwtDecoder, OAuth2TokenValidator<Jwt> audienceValidator) {
    // Combine issuer/time validators with audience validator
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer("${spring.security.oauth2.resourceserver.jwt.issuer-uri}");
    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
    jwtDecoder.setJwtValidator(validator);
    return jwtDecoder;
}
```

6) Remove the in-memory user store and password encoder (no longer used):
- Delete the userDetailsService() bean and the PasswordEncoder bean from WebSecurityConfig.

---

## 4) Optional: Method-level RBAC
If you added `@EnableMethodSecurity`, you can annotate endpoints/services:

```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<UploadResponse> uploadStatement(...) { ... }

@PreAuthorize("hasAnyRole('AUDIT','ADMIN')")
public ResponseEntity<AuditLogPage> getFilteredAuditLogs(...) { ... }
```

This complements — not replaces — the HTTP matcher rules.

---

## 5) Update OpenAPI to Bearer JWT
Replace Basic Auth with Bearer JWT in the OpenAPI spec file: openapi/statement-service-v1-openapi.yaml

1) Add/replace securitySchemes:
```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

2) Update protected operations to require bearerAuth. For example, /upload and /audit/logs:
```yaml
paths:
  /upload:
    post:
      security:
        - bearerAuth: []
  /audit/logs:
    get:
      security:
        - bearerAuth: []
```

3) Remove legacy basicAuth references anywhere in the file.

---

## 6) Keycloak realm/client/role setup

Create or reuse a realm (e.g., statement-realm). Then:

1) Roles
- Create realm roles:
  - ADMIN
  - AUDIT

2) Client (resource server audience)
- Create a client named statement-service
- Access Type: bearer-only (Keycloak versions <= 18) or Standard (confidential/public) as appropriate; the service itself only validates tokens and does not perform browser redirects.
- Ensure the client appears as an audience in issued tokens (via client scope or audience mapper) when calling this API.

3) Token mappers
- Ensure realm roles are included in tokens: realm_access.roles
- If you prefer client roles, add role mappings under the client and ensure resource_access["statement-service"].roles is populated.

4) Users/Groups
- Assign ADMIN to users who can call Admin endpoints (e.g., POST /upload)
- Assign AUDIT (or ADMIN) to users who can call Audit endpoints

5) OIDC endpoints
- Issuer: https://<keycloak>/realms/statement-realm
- JWKs: https://<keycloak>/realms/statement-realm/protocol/openid-connect/certs

---

## 7) Verification checklist and curl examples

Checklist
- Build and start the service with the new dependencies and config.
- Obtain a JWT from Keycloak for a user with ADMIN role.
- Call a public endpoint without a token (should succeed):
  - GET /api/v1/statements/search
- Call an admin endpoint without a token (should 401):
  - POST /api/v1/statements/upload
- Call the same admin endpoint with ADMIN token (should 201 on success):
  - POST /api/v1/statements/upload
- Call an audit endpoint with AUDIT token (should 200).

Example: call audit logs with a bearer token
```bash
TOKEN="$(printf 'eyJhbGciOi...<snip>...')"
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/statements/audit/logs?page=0&size=10"
```

If behind a reverse proxy that sets X-Forwarded-* headers, ensure proper absolute URL building for signed links (ForwardedHeaderFilter or server.forward-headers-strategy: framework).

---

## 8) Rollback and notes
- You can temporarily keep Basic auth by retaining `.httpBasic()` alongside the JWT resource server while migrating, but do not expose admin endpoints to both in production.
- This service is stateless. Keep SessionCreationPolicy.STATELESS and CSRF disabled for API endpoints.
- Swagger/OpenAPI endpoints remain in the whitelist; they do not require auth unless you remove them from the whitelist.

---

## Summary of code edits (to apply manually)
- pom.xml: add spring-boot-starter-oauth2-resource-server (+ optional jose).
- application.yml: add spring.security.oauth2.resourceserver.jwt.issuer-uri (and optional audience).
- WebSecurityConfig:
  - Replace `.httpBasic()` with `.oauth2ResourceServer().jwt(...)`.
  - Add `jwtAuthenticationConverter()` bean mapping Keycloak roles to ROLE_*.
  - Update authorization rules: admin -> hasRole('ADMIN'), audit -> hasAnyRole('AUDIT','ADMIN').
  - Remove `UserDetailsService` and `PasswordEncoder` beans used for Basic auth.
- Optional: add `@EnableMethodSecurity` and @PreAuthorize annotations on protected methods.
- OpenAPI YAML: switch to bearerAuth security scheme and apply to protected operations.

Once these changes are applied, the service will authenticate requests using Keycloak-issued JWTs and enforce RBAC via roles ADMIN and AUDIT.
