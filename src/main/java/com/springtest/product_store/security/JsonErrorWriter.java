package com.springtest.product_store.security;

import com.springtest.product_store.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

// Writes the standard ErrorResponse JSON from inside the security filter chain,
// where @RestControllerAdvice can't reach (401, 403, and 503 from JwtAuthFilter).
// Spring MVC hasn't set the locale yet at this point, so it's resolved here from
// the request with the same LocaleResolver (Accept-Language: ar -> Arabic).
@Component
public class JsonErrorWriter {

    private final JsonMapper jsonMapper;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public JsonErrorWriter(JsonMapper jsonMapper, MessageSource messageSource, LocaleResolver localeResolver) {
        this.jsonMapper = jsonMapper;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String messageCode)
            throws IOException {
        String message = messageSource.getMessage(messageCode, null, localeResolver.resolveLocale(request));
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(),
                new ErrorResponse(status, message, LocalDateTime.now().toString()));
    }
}
