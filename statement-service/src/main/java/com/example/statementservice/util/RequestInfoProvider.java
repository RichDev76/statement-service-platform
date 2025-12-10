package com.example.statementservice.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestInfoProvider {

    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String UNKNOWN = "unknown";
    public static final String SYSTEM_DEFAULT = "system";
    public static final String JWT_CLAIM_PREFERRED_USERNAME = "preferred_username";

    public RequestInfo get() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String clientIp = request != null ? request.getRemoteAddr() : UNKNOWN;
        String userAgent = request != null ? request.getHeader(USER_AGENT_HEADER) : UNKNOWN;
        String performedBy = resolveUsername();

        return new RequestInfo(clientIp, userAgent, performedBy);
    }

    private String resolveUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;

            if (auth == null || !auth.isAuthenticated()) {
                return SYSTEM_DEFAULT;
            }

            // Prefer Keycloak's preferred_username claim when using JWT auth
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String preferredUsername = jwt.getClaimAsString(JWT_CLAIM_PREFERRED_USERNAME);

                if (preferredUsername != null && !preferredUsername.isBlank()) {
                    return preferredUsername;
                }
            }

            // Fallback to the standard Spring Security username
            String name = auth.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (Exception ignored) {
            // swallow and fall through to system
        }

        return SYSTEM_DEFAULT;
    }
}