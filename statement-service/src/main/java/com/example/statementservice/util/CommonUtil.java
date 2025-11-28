package com.example.statementservice.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

public class CommonUtil {

    public static URI buildProblemDetailTypeURI(HttpServletRequest request, String contextPath) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder baseUrl = new StringBuilder(scheme).append("://").append(serverName);

        if (!((scheme.equals("http") && serverPort == 80) || (scheme.equals("https") && serverPort == 443))) {
            baseUrl.append(":").append(serverPort);
        }

        if (contextPath != null && !contextPath.isEmpty()) {
            baseUrl.append(contextPath);
        }

        return URI.create(baseUrl.toString());
    }
}
