package com.springtest.product_store.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 401 for requests that aren't authenticated (RFC 6750). If a Bearer token was sent,
// JwtAuthFilter rejected it (malformed, bad signature, expired or revoked).
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonErrorWriter jsonErrorWriter;

    public RestAuthenticationEntryPoint(JsonErrorWriter jsonErrorWriter) {
        this.jsonErrorWriter = jsonErrorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean bearerSent = authHeader != null && authHeader.startsWith("Bearer ");

        if (bearerSent) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            jsonErrorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "auth.invalidToken");
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            jsonErrorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "auth.required");
        }
    }
}
