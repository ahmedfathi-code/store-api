package com.springtest.product_store.security;

import com.springtest.product_store.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

// Writes the standard ErrorResponse JSON from inside the security filter chain,
// where @RestControllerAdvice can't reach (401, 403, and 503 from JwtAuthFilter)
@Component
public class JsonErrorWriter {

    private final JsonMapper jsonMapper;

    public JsonErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(),
                new ErrorResponse(status, message, LocalDateTime.now().toString()));
    }
}
