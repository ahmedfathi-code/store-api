package com.springtest.product_store.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 403 for authenticated users without the required role (e.g. USER on product writes),
// with the same ErrorResponse JSON as every other error
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonErrorWriter jsonErrorWriter;

    public RestAccessDeniedHandler(JsonErrorWriter jsonErrorWriter) {
        this.jsonErrorWriter = jsonErrorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        jsonErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
    }
}
